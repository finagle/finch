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
import com.twitter.util.{Future, Await}

import _root_.argonaut._, _root_.argonaut.Argonaut._
import io.finch.{Endpoint => _, _}

// import the pipe `!` operator
import io.finch.argonaut._             // import finch-json classes such as `Json`
import io.finch.request._          // import request readers such as `RequiredParam`
import io.finch.request.items._    // import request items for error handling
import io.finch.response._         // import response builders such as `BadRequest`
import io.finch.route._            // import route combinators

/**
 * To run the demo from console use:
 *
 * > sbt 'project demo' 'run io.finch.demo.Main'
 */
object Main extends App {

  // Serve the backend using the Httpx protocol.
  val _ = Await.ready(Httpx.serve(":8080", Demo.backend))
}

object Demo {

  import model._
  import endpoint._

  val Secret = "open sesame"

  // A Finagle filter that authorizes a request: performs conversion `HttpRequest` => `AuthRequest`.
  val authorize = new Filter[HttpRequest, HttpResponse, AuthRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[AuthRequest, HttpResponse]): Future[HttpResponse] = for {
      `X-Secret` <- headerOption("X-Secret")(req)
      rep <- `X-Secret` collect {
        case Secret => service(AuthRequest(req))
      } getOrElse Unauthorized().toFuture
    } yield rep
  }

  val handleDomainErrors: PartialFunction[Throwable, HttpResponse] = {
    case UserNotFound(id) => BadRequest(Json("error" := "user_not_found", "id" := id))
  }

  val handleRequestReaderErrors: PartialFunction[Throwable, HttpResponse] = {
    case NotPresent(ParamItem(p)) => BadRequest(
      Json("error" := "param_not_present", "param" := p)
    )

    case NotPresent(BodyItem) => BadRequest(Json("error" := "body_not_present"))

    case NotParsed(ParamItem(p), _, _) => BadRequest(
      Json("error" := "param_not_parsed", "param" := p)
    )

    case NotParsed(BodyItem, _, _) => BadRequest(Json("error" := "body_not_parsed"))

    case NotValid(ParamItem(p), rule) => BadRequest(
      Json("error" := "param_not_valid", "param" := p, "rule" := rule)
    )
  }

  val handleRouterErrors: PartialFunction[Throwable, HttpResponse] = {
    case RouteNotFound(route) => NotFound(Json("error" := "route_not_found", "route" := route))
  }

  // A simple Finagle filter that handles all the exceptions, which might be thrown by
  // a request reader of one of the REST services.
  val handleExceptions = new SimpleFilter[HttpRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]): Future[HttpResponse] = service(req) handle
      (handleDomainErrors orElse handleRequestReaderErrors orElse handleRouterErrors orElse {
        case _ => InternalServerError()
      })
  }

  // An API endpoint.
  val api: Service[AuthRequest, Json] = users | tickets

  // An HTTP endpoint with exception handler and Auth filter.
  val backend: Service[HttpRequest, HttpResponse] =
    handleExceptions ! authorize ! (api ! TurnIntoHttp[Json])

}
