/*
 * Copyright 2015, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s): -
 */

package io.finch.demo

import com.twitter.finagle.{Filter, SimpleFilter, Service, Httpx}
import com.twitter.util.Await

import io.finch.{Endpoint => _, _} // import the pipe `!` operator
import io.finch.json._             // import finch-json classes such as `Json`
import io.finch.request._          // import request readers such as `RequiredParam`
import io.finch.response._         // import response builders such as `BadRequest`
import io.finch.route._            // import route combinators

/**
 * To run the demo from console use:
 *
 * > sbt 'project demo' 'run io.finch.demo.Main'
 */
object Main extends App {

  import model._
  import endpoint._

  // A Finagle filter that authorizes a request: performs conversion `HttpRequest` => `AuthRequest`.
  val authorize = new Filter[HttpRequest, HttpResponse, AuthRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[AuthRequest, HttpResponse]) = for {
      secret <- RequiredHeader("Secret")(req)
      rep <- if (secret == "open sesame") service(AuthRequest(req, secret))
      else Unauthorized(Json.obj("error" -> "wrong_secret")).toFuture
    } yield rep
  }

  // A simple Finagle filter that handles all the exceptions, which might be thrown by
  // a request reader of one of the REST services.
  val handleExceptions = new SimpleFilter[HttpRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]) = service(req) handle {
      case UserNotFound(id) => BadRequest(Json.obj("error" -> "user_not_found", "id" -> id))
      case NotPresent(item) => BadRequest(Json.obj("error" -> "item_not_found", "item" -> item))
      case NotParsed(item, _, _) => BadRequest(Json.obj("error" -> "item_not_parsed", "item" -> item))
      case NotValid(item, rule) => BadRequest(Json.obj("error" -> "item_not_valid", "rule" -> rule))
      case _ => InternalServerError()
    }
  }

  // An HTTP endpoint that is composed of user and ticket endpoints.
  val httpBackend: Endpoint[AuthRequest, HttpResponse] =
    (users | tickets) /> { _ ! TurnModelIntoJson ! TurnIntoHttp[Json] }

  // A backend endpoint with exception handler and Auth filter.
  val backend: Endpoint[HttpRequest, HttpResponse] =
    httpBackend /> { handleExceptions ! authorize ! _ }

  // Serve the backend using the Httpx protocol.
  Await.ready(Httpx.serve(":8080", backend))
}
