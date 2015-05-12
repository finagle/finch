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

package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Method
import com.twitter.util.Future
import io.finch.response.{EncodeResponse, Ok}
import shapeless._
import shapeless.ops.coproduct.{Mapper, Unifier}

/**
 * This package contains various of functions and types that enable _router combinators_ in Finch. A Finch
 * [[io.finch.route.Router Router]] is an abstraction that is responsible for routing the HTTP requests using their
 * method and path information. There are two types of routers in Finch: [[io.finch.route.Router0 Router0]] and
 * [[io.finch.route.RouterN RouterN]]. `Router0` matches the route and returns an `Option` of the rest of the route.
 * `RouterN[A]` (or just `Router[A]`) in addition to the `Router0` behaviour extracts a value of type `A` from the
 * route.
 *
 * A [[io.finch.route.Router Router]] that maps route to a [[com.twitter.finagle.Service Service]] is called an
 * [[io.finch.route.Endpoint Endpoint]]. An endpoint `Req => Rep` might be implicitly converted into a
 * `Service[Req, Rep]`. Thus, the following example is a valid Finch code:
 *
 * {{{
 *   def hello(s: String) = new Service[HttRequest, HttpResponse] {
 *     def apply(req: HttpRequest) = Ok(s"Hello $name!").toFuture
 *   }
 *
 *   Httpx.serve(
 *     new InetSocketAddress(8081),
 *     Get / string /> hello // will be implicitly converted into service
 *   )
 * }}}
 */
package object route extends LowPriorityRouterImplicits {

  object tokens {
    //
    // ADT that describes a route abstraction.
    //
    private[route] sealed trait RouteToken
    private[route] case class MethodToken(m: Method) extends RouteToken
    private[route] case class PathToken(p: String) extends RouteToken
  }

  import tokens._

  private[route] type Route = List[RouteToken]

  /**
   * A case class that enables the following syntax:
   *
   * {{{
   *   val r: Router[Int / String] = Get / int / string
   *   val s: String = p /> { case i / s => s + i }
   * }}}
   */
  case class /[+A, +B](_1: A, _2: B)

  /**
   * A user friendly alias for [[io.finch.route.RouterN RouterN]].
   */
  type Router[+A] = RouterN[A]

  /**
   * An alias for [[io.finch.route.Router Router]] that maps route to a [[com.twitter.finagle.Service Service]].
   */
  type Endpoint[-A, +B] = Router[Service[A, B]]

  /**
   * An exception, which is thrown by router in case of missing route `r`.
   */
  case class RouteNotFound(r: String) extends Exception(s"Route not found: $r")

  /**
   * Converts `Req` to `Route`.
   */
  private[finch] def requestToRoute[Req](req: HttpRequest): Route =
    (MethodToken(req.method): RouteToken) :: (req.path.split("/").toList.drop(1) map PathToken)

  /**
   * Implicitly converts the given `Router[Service[_, _]]` into a service.
   */
  implicit def endpointToService[Req, Rep](
    r: RouterN[Service[Req, Rep]]
  )(implicit ev: Req => HttpRequest): Service[Req, Rep] = new Service[Req, Rep] {
    def apply(req: Req): Future[Rep] = r(requestToRoute[Req](req)) match {
      case Some((Nil, service)) => service(req)
      case _ => RouteNotFound(s"${req.method.toString.toUpperCase} ${req.path}").toFutureException[Rep]
    }
  }

  /**
   * A polymorphic function value that accepts types that can be encoded as a
   * response.
   */
  object EncodeAll extends Poly1 {
    implicit val response: Case.Aux[HttpResponse, HttpResponse] = at(identity)
    implicit def any[A](implicit encoder: EncodeResponse[A]): Case.Aux[A, HttpResponse] =
      at[A](Ok(_))
  }

  /**
   * An implicit conversion that turns any coproduct endpoint where all elements
   * can be converted into responses into an endpoint that returns responses.
   */
  implicit def coproductEndpointToHttpResponse[A, C <: Coproduct, O <: Coproduct](
    e: Endpoint[A, C]
  )(implicit
    mapper: Mapper.Aux[EncodeAll.type, C, O],
    unifier: Unifier.Aux[O, HttpResponse]
  ): Endpoint[A, HttpResponse] = e.map { service =>
    new Service[A, HttpResponse] {
      def apply(a: A): Future[HttpResponse] = service(a).map(c => unifier(mapper(c)))
    }
  }

  /**
   * An implicit conversion that turns any endpoint with an output type that can
   * be converted into a response into an endpoint that returns responses.
   */
  implicit def endpointToHttpResponse[A, B](e: Endpoint[A, B])(implicit
    encoder: EncodeResponse[B]
  ): Endpoint[A, HttpResponse] = e.map { service =>
    new Service[A, HttpResponse] {
      def apply(a: A): Future[HttpResponse] = service(a).map(b => Ok(encoder(b)))
    }
  }

  /**
   * A helper method that supports mapping a service's output into the first
   * element of a coproduct.
   */
  private[this] def intoLeft[A, B, C <: Coproduct](service: Service[A, B]): Service[A, B :+: C] =
    new Service[A, B :+: C] {
      def apply(a: A): Future[B :+: C] = service(a).map(Inl(_))
    }

  /**
   * A helper method that supports mapping a service's output into the second
   * element of coproduct.
   */
  private[this] def intoSecond[A, B, C](service: Service[A, C]): Service[A, B :+: C :+: CNil] =
    new Service[A, B :+: C :+: CNil] {
      def apply(a: A): Future[B :+: C :+: CNil] = service(a).map(c => Inr(Inl(c)))
    }

  /**
   * A helper method that supports mapping a service's output into the tail of a
   * coproduct.
   */
  private[this] def intoRight[A, B, C <: Coproduct](service: Service[A, C]): Service[A, B :+: C] =
    new Service[A, B :+: C] {
      def apply(a: A): Future[B :+: C] = service(a).map(Inr(_))
    }

  /**
   * Implicit class that provides `:|:` and other operations on any coproduct endpoint.
   */
  final implicit class CoproductEndpointOps[A, C <: Coproduct](self: Endpoint[A, C]) {
    def :|:[B](that: Endpoint[A, B]): Endpoint[A, B :+: C] =
      new RouterN[Service[A, B :+: C]] {
        def apply(route: Route): Option[(Route, Service[A, B :+: C])] =
          (that(route), self(route)) match {
            case (aa @ Some((ar, av)), cc @ Some((cr, cv))) =>
              if (ar.length <= cr.length) Some((ar, intoLeft(av))) else Some((cr, intoRight(cv)))
            case (a, b) => a.map {
              case (r, v) => (r, intoLeft(v))
            } orElse b.map {
              case (r, v) => (r, intoRight(v))
            }
          }

        override def toString = s"(${that.toString}|${self.toString})"
      }
  }

  /**
   * Implicit class that provides `:|:` on any endpoint.
   */
  final implicit class ValueEndpointOps[A, C](self: Endpoint[A, C]) {
    def :|:[B](that: Endpoint[A, B]): Endpoint[A, B :+: C :+: CNil] =
      new RouterN[Service[A, B :+: C :+: CNil]] {
        def apply(route: Route): Option[(Route, Service[A, B :+: C :+: CNil])] =
          (that(route), self(route)) match {
            case (aa @ Some((ar, av)), cc @ Some((cr, cv))) =>
              if (ar.length <= cr.length) Some((ar, intoLeft(av))) else Some((cr, intoSecond(cv)))
            case (a, b) => a.map {
              case (r, v) => (r, intoLeft(v))
            } orElse b.map {
              case (r, v) => (r, intoSecond(v))
            }
          }

        override def toString = s"(${that.toString}|${self.toString})"
      }
  }

  /**
   * Add `/>` compositor to `RouterN` to compose it with function of two argument.
   */
  implicit class RArrow2[A, B](val r: RouterN[A / B]) extends AnyVal {
    def />[C](fn: (A, B) => C): RouterN[C] =
      r.map { case a / b => fn(a, b) }
  }

  /**
   * Add `/>` compositor to `RouterN` to compose it with function of three argument.
   */
  implicit class RArrow3[A, B, C](val r: RouterN[A / B / C]) extends AnyVal {
    def />[D](fn: (A, B, C) => D): RouterN[D] =
      r.map { case a / b / c => fn(a, b, c) }
  }

  /**
   * Add `/>` compositor to `RouterN` to compose it with function of four argument.
   */
  implicit class RArrow4[A, B, C, D](val r: RouterN[A / B / C / D]) extends AnyVal {
    def />[E](fn: (A, B, C, D) => E): RouterN[E] =
      r.map { case a / b / c / d => fn(a, b, c, d) }
  }

  implicit def intToMatcher(i: Int): Router0 = new Matcher(i.toString)
  implicit def stringToMatcher(s: String): Router0 = new Matcher(s)
  implicit def booleanToMather(b: Boolean): Router0 = new Matcher(b.toString)

  /**
   * An universal matcher.
   */
  case class Matcher(s: String) extends Router0 {
    def apply(route: Route): Option[Route] = for {
      PathToken(ss) <- route.headOption if ss == s
    } yield route.tail
    override def toString = s
  }

  private[route] def stringToSomeValue[A](fn: String => A)(s: String): Option[A] =
    try Some(fn(s)) catch { case _: IllegalArgumentException => None }

  /**
   * A [[io.finch.route.RouterN RouterN]] that extracts a path token.
   */
  object PathTokenExtractor extends RouterN[String] {
    override def apply(route: Route): Option[(Route, String)] = for {
      PathToken(ss) <- route.headOption
    } yield (route.tail, ss)
  }

  /**
   * An universal extractor that extracts some value of type `A` if it's possible to fetch the value from the string.
   */
  case class Extractor[A](name: String, f: String => Option[A]) extends RouterN[A] {
    def apply(route: Route): Option[(Route, A)] = PathTokenExtractor.embedFlatMap(f)(route)
    def apply(n: String): Extractor[A] = copy[A](name = n)
    override def toString = s":$name"
  }

  /**
   * A [[io.finch.route.Router0 Router0]] that matches the given HTTP method `m` in the route.
   */
  case class MethodMatcher(m: Method) extends Router0 {
    def apply(route: Route): Option[Route] = for {
      MethodToken(mm) <- route.headOption if m == mm
    } yield route.tail
    override def toString = s"${m.toString.toUpperCase}"
  }

  //
  // A group of routers that matches HTTP methods.
  //
  object Get extends MethodMatcher(Method.Get)
  object Post extends MethodMatcher(Method.Post)
  object Patch extends MethodMatcher(Method.Patch)
  object Delete extends MethodMatcher(Method.Delete)
  object Head extends MethodMatcher(Method.Head)
  object Options extends MethodMatcher(Method.Options)
  object Put extends MethodMatcher(Method.Put)
  object Connect extends MethodMatcher(Method.Connect)
  object Trace extends MethodMatcher(Method.Trace)

  /**
   * A [[io.finch.route.Router0 Router0]] that skips one route token.
   */
  object * extends Router0 {
    def apply(route: Route): Option[Route] = Some(route.drop(1))
    override def toString = "*"
  }

  /**
   * A [[io.finch.route.Router0 Router0]] that skips all route tokens.
   */
  object ** extends Router0 {
    def apply(route: Route): Option[Route] = Some(Nil)
    override def toString = "**"
  }

  /**
   * A [[io.finch.route.RouterN RouterN]] that extract an integer from the route.
   */
  object int extends Extractor("int", stringToSomeValue(_.toInt))

  /**
   * A [[io.finch.route.RouterN RouterN]] that extract a long value from the route.
   */
  object long extends Extractor("long", stringToSomeValue(_.toLong))

  /**
   * A [[io.finch.route.RouterN RouterN]] that extract a string value from the route.
   */
  object string extends Extractor("string", Some(_))

  /**
   * A [[io.finch.route.RouterN RouterN]] that extract a boolean value from the route.
   */
  object boolean extends Extractor("boolean", stringToSomeValue(_.toBoolean))
}
