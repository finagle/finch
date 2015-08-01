/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.petstore

import _root_.argonaut._, Argonaut._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Future
import io.finch.argonaut._
import io.finch.request._
import io.finch.request.items._
import io.finch.response._
import io.finch.route._

/**
 * Tells the API how to respond when certain exceptions are thrown.
 */
trait ErrorHandling {
  /**
   * Tells the service how to handle certain types of servable errors (i.e. PetstoreError)
   */
  def errorHandler: PartialFunction[Throwable, Response] = {
    case RouteNotFound(route) => NotFound(
      Map("error" -> "route_not_found", "route" -> route).asJson
    )
    case NotPresent(ParamItem(p)) => BadRequest(
      Map("error" -> "param_not_present", "param" -> p).asJson
    )
    case NotPresent(BodyItem) => BadRequest(
      Map("error" -> "body_not_present").asJson
    )
    case NotParsed(ParamItem(p), _, _) => BadRequest(
      Map("error" -> "param_not_parsed", "param" -> p).asJson
    )
    case NotParsed(BodyItem, _, _) => BadRequest(
      Map("error" -> "body_not_parsed").asJson
    )
    case NotValid(ParamItem(p), rule) => BadRequest(
      Map("error" -> "param_not_valid", "param" -> p, "rule" -> rule).asJson
    )
    // Domain errors
    case error: PetstoreError => NotFound(
      Map("error" -> error.message).asJson
    )
  }

  /**
   * A simple Finagle filter that handles all the exceptions, which might be thrown by
   * a request reader of one of the REST services.
   */
  def handleExceptions: SimpleFilter[Request,Response] = new SimpleFilter[Request, Response] {
    def apply(req: Request, service: Service[Request, Response]): Future[Response] =
      service(req).handle(errorHandler)
  }
}
