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

import org.jboss.netty.handler.codec.http.HttpMethod
import com.twitter.finagle.http.path.Path
import com.twitter.finagle.{Filter, Service}

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
  def route: PartialFunction[(HttpMethod, Path), Service[Req, Rep]]

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
  def orElse(that: PartialFunction[(HttpMethod, Path), Service[Req, Rep]]): Endpoint[Req, Rep] =
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
   * Composes this endpoint with given ''filter''.
   *
   * @param filter the filter to compose
   * @tparam RepOut the response type
   *
   * @return an endpoint composed with filter
   */
  def ![RepOut](filter: Filter[Req, RepOut, Req, Rep]) = filter ! self

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
}
