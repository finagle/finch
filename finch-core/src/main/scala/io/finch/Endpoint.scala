/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
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

package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Method
import com.twitter.finagle.httpx.path.Path
import com.twitter.finagle.httpx.service.NotFoundService

/**
 * A REST API endpoint that primary defines a ''route'' and might be converted
 * into a finagled service with ''toService'' method.
 *
 * @tparam Rep a response type
 */
trait Endpoint[Req <: HttpRequest, Rep] { self =>

  /**
   * A rich route of this endpoint.
   *
   * @return a route of this endpoint
   */
  def route: PartialFunction[(Method, Path), Service[Req, Rep]]

  /**
   * Sends a request ''req'' to this Endpoint.
   *
   * @param req the request to send
   * @return a response wrapped with ''Future''
   */
  def apply(req: Req) = route(req.method -> Path(req.path))(req)

  /**
   * Combines this endpoint with ''that'' endpoint. A new endpoint
   * contains routes of both this and ''that'' endpoint.
   *
   * @param that the endpoint to be combined with
   *
   * @return a new endpoint
   */
  def orElse(that: Endpoint[Req, Rep]): Endpoint[Req, Rep] = orElse(that.route)

  /**
   * Combines this endpoint with ''that'' partial function that defines
   * a route. A new endpoint contains routes of both this endpoint and ''that''
   * partial function
   *
   * @param that the partial function to be combined with
   *
   * @return a new endpoint
   */
  def orElse(that: PartialFunction[(Method, Path), Service[Req, Rep]]): Endpoint[Req, Rep] =
    new Endpoint[Req, Rep] {
      def route = self.route orElse that
    }

  /**
   * Applies given function ''fn'' to every route's endpoints of this endpoint.
   *
   * @param fn the function to be applied
   *
   * @return a new endpoint
   */
  def andThen[ReqOut <: HttpRequest, RepOut](fn: Service[Req, Rep] => Service[ReqOut, RepOut]) =
    new Endpoint[ReqOut, RepOut] {
      def route = self.route andThen fn
    }

  /**
   * Composes this endpoint with given ''next'' service.
   *
   * @param next the service to compose
   * @tparam RepOut the response type
   *
   * @return an endpoint composed with filter
   */
  def ![RepOut](next: Service[Rep, RepOut]) =
    andThen { service =>
      new Service[Req, RepOut] {
        def apply(req: Req) = service(req) flatMap next
      }
    }

  /**
   * Converts this endpoint into a finagled service.
   *
   * @return a finagled service
   */
  def toService = new Service[Req, Rep] {
    def apply(req: Req) = self(req)
  }
}

/**
 * A companion object for ''Endpoint''
 */
object Endpoint {

  /**
   * Joins given sequence of endpoints by orElse-ing them.
   *
   * @param endpoints the sequence of endpoints to join
   * @tparam Req a request type
   * @tparam Rep a response type
   *
   * @return a joined endpoint
   */
  def join[Req <: HttpRequest, Rep](endpoints: Endpoint[Req, Rep]*) = endpoints.reduce(_ orElse _)

  /**
   * A robust 404 respond for missing endpoints.
   */
  val NotFound = new Endpoint[HttpRequest, HttpResponse] {
    val underlying = new NotFoundService[HttpRequest]
    def route = { case _ => underlying }
  }
}
