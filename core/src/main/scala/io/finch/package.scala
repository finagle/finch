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

package io

import com.twitter.finagle.httpx.path.Path
import io.finch.response.{Ok, EncodeResponse}

import com.twitter.finagle.httpx
import com.twitter.util.Future
import com.twitter.finagle.Service
import com.twitter.finagle.Filter

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and types atop of Finagle
 * for writing lightweight HTTP services. It roughly contains three packages: [[io.finch.route]], [[io.finch.request]],
 * [[io.finch.response]], which correspond to three simple steps to a robust REST/HTTP API:
 *
 * Step 1. Routing the HTTP requests to a `Service`.
 *
 * The [[io.finch.route.Router Router]] abstraction routes the requests depending on their path and method information.
 * `Router` combinator provides a bunch of predefined routers handling separated parts of a route. `Router`s might be
 * composed with either `/` (`andThen`) or `/>` (`map`) operator. There is also `|` (`orElse`) operator that combines
 * two routers in terms of the inclusive or operator.
 *
 * {{{
 *   val router: Endpoint[HttpRequest, HttpResponse] =
 *     Get / ("users" | "user") / int /> GetUser
 * }}}
 *
 * Step 2. Reading the HTTP requests in a `Service`.
 *
 * The [[io.finch.request.RequestReader RequestReader]] abstraction is responsible for reading any details form the HTTP
 * request. `RequestReader` is composable in both ways: via the monadic API (using the for-comprehension, i.e.,
 * `flatMap`) and via the applicative API (using the `~` operator). These approaches define an unlimited number of
 * readers out the plenty of predefined ones.
 *
 * {{{
 *   val pagination: RequestReader[(Int, Int)] =
 *     OptionalParam("offset").as[Int] ~ OptionalParam("limit").as[Int] map {
 *       case offset ~ limit => (offset.getOrElse(0), limit.getOrElse(100))
 *     }
 *   val p = pagination(request)
 * }}}
 *
 * Step 3. Building the HTTP responses in a `Service`.
 *
 * The [[io.finch.response.ResponseBuilder ResponseBuilder]] abstraction provides a convenient way of building the HTTP
 * responses any type. In fact, `ResponseBuilder` is a function that takes some content and builds an HTTP response of a
 * type depending on a content. There are plenty of predefined builders that might be used directly.
 *
 * {{{
 *   val ok: HttpResponse = Ok("Hello, world!") // plain/text HTTP response with status code 200
 * }}}
 */
package object finch {

  /**
   * An alias for [[com.twitter.finagle.httpx.Request httpx.Request]].
   */
  type HttpRequest = httpx.Request

  /**
   * An alias for [[com.twitter.finagle.httpx.Response httpx.Response]].
   */
  type HttpResponse = httpx.Response

  /**
   * Alters any object within a `toFuture` method.
   *
   * @param any an object to be altered
   *
   * @tparam A an object type
   */
  implicit class AnyOps[A](val any: A) extends AnyVal {

    /**
     * Converts this ''any'' object into a ''Future''
     */
    def toFuture: Future[A] = Future.value[A](any)
  }

  /**
   * Alters any throwable with a `toFutureException` method.
   *
   * @param t a throwable to be altered
   */
  implicit class ThrowableOps(val t: Throwable) extends AnyVal {

    /**
     * Converts this throwable object into a `Future` exception.
     */
    def toFutureException[A]: Future[A] = Future.exception[A](t)
  }

  /**
   * Alters underlying filter within `!` methods composing a filter with a given endpoint or withing a next filter.
   *
   * @param filter a filter to be altered
   */
  implicit class FilterOps[ReqIn, ReqOut, RepIn, RepOut](
      val filter: Filter[ReqIn, RepOut, ReqOut, RepIn]) extends AnyVal {

    /**
     * Composes this filter within given endpoint ''next'' endpoint.
     *
     * @param next an endpoint to compose
     *
     * @return an endpoint composed with filter
     */
    def !(next: Endpoint[ReqOut, RepIn]): Endpoint[ReqIn, RepOut] =
      next andThen { service =>
        filter andThen service
      }

    /**
     * Composes this filter within given ''next'' filter.
     *
     * @param next the next filter in the chain
     * @tparam Req the request type
     * @tparam Rep the response type
     *
     * @return a filter composed within next filter
     */
    def ![Req, Rep](next: Filter[ReqOut, RepIn, Req, Rep]): Filter[ReqIn, RepOut, Req, Rep] =
      filter andThen next

    /**
     * Composes this filter within given ''next'' service.
     *
     * @param next the service to compose
     *
     * @return a service composed with filter
     */
    def !(next: Service[ReqOut, RepIn]): Service[ReqIn, RepOut] = filter andThen next
  }

  /**
   * Alters underlying service within ''afterThat'' method composing a service with a given filter.
   *
   * @param service a service to be altered
   *
   * @tparam RepIn a input response type
   */
  implicit class ServiceOps[Req, RepIn](service: Service[Req, RepIn]) {

    /**
     * Composes this service with given ''next'' service.
     *
     * @param next a service to compose
     * @tparam RepOut an output response type
     *
     * @return a new service compose with other service.
     */
    def ![RepOut](next: Service[RepIn, RepOut]): Service[Req, RepOut]=
      new Service[Req, RepOut] {
        def apply(req: Req): Future[RepOut] = service(req) flatMap next
      }
  }

  /**
   * Allows for the creation of ''Endpoint''s without an explicit service.
   *
   * {{{
   *   Endpoint {
   *     Method.Get -> Root / "hello" => Ok("world").toFuture
   *   }
   * }}}
   *
   * @param f a future to implicitly convert
   *
   * @return a service generated by ignoring the ''Req'' and returning the ''Future[Rep]''
   */
  implicit def futureToService[Req, Rep](f: Future[Rep]): Service[Req, Rep] =
    new Service[Req, Rep] {
      def apply(req: Req): Future[Rep] = f
    }

  /**
   * Allows to use an ''Endpoint'' when ''Service'' is expected. The implicit
   * conversion is possible if there is an implicit view from ''Req'' to
   * ''HttpRequest'' available in the scope.
   *
   * {{{
   *   val e: Endpoint[HttpRequest, HttpResponse] = ???
   *   Httpx.serve(new InetSocketAddress(8081), e)
   * }}}
   *
   * @param e the endpoint to convert
   * @param ev the evidence of implicit view
   * @tparam Req the request type
   * @tparam Rep the response type
   *
   * @return a service that delegates the requests to the underlying endpoint
   */
  implicit def endpointToService[Req, Rep](e: Endpoint[Req, Rep])(implicit ev: Req => HttpRequest): Service[Req, Rep] =
    new Service[Req, Rep] {
      def apply(req: Req): Future[Rep] = e.route(req.method -> Path(req.path))(req)
    }
}
