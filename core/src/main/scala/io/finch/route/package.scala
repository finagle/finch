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

/**
 * This package contains various of functions and types that enable _router
 * combinators_ in Finch. A Finch [[route.Router]] is an abstraction that
 * is responsible for routing the HTTP requests using their method and path
 * information. There are two types of routers in Finch: [[route.Router0]]
 * and [[route.RouterN]]. `Router0` matches the route and returns an `Option`
 * of the rest of the route. `RouterN[A]` (or just `Router[A]`) in addition
 * to the `Router0` behaviour extracts a value of type `A` from the route.
 *
 * A [[route.Router]] that extracts [[Service]] is called an endpoint. An
 * endpoint `Req => Rep` might be implicitly converted into `Service[Req, Rep]`.
 * Thus, the following example is a valid Finch code:
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
package object route {

  //
  // ADT that describes a route abstraction.
  //
  private[route] sealed trait RouteToken
  private[route] case class MethodToken(m: Method) extends RouteToken
  private[route] case class PathToken(p: String) extends RouteToken

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
   * A user friendly alias for [[RouterN]].
   */
  type Router[+A] = RouterN[A]

  /**
   * An exception, which is thrown by router in case of missing route `r`.
   */
  case class RouteNotFound(r: String) extends Exception(s"Route not found: $r")

  private[route] def requestToRoute[Req](req: Req)(implicit ev: Req => HttpRequest): Route =
    MethodToken(req.method) :: (req.path.split("/").toList.tail map PathToken)

  /**
   * Implicitly converts the given `Router[Service[_, _]]` into a service.
   */
  implicit def routerOfServiceToService[Req, Rep](
    r: RouterN[Service[Req, Rep]]
  )(implicit ev: Req => HttpRequest): Service[Req, Rep] = new Service[Req, Rep] {
    def apply(req: Req): Future[Rep] = {
      val path = requestToRoute(req)
      r(path) match {
        case Some((Nil, service)) => service(req)
        case _ => RouteNotFound(req.path).toFutureException
      }
    }
  }

  /**
   * Implicitly converts the given `Router[Future[_]]` into a service.
   */
  implicit def routerOfFutureToService[Req, Rep](
    r: RouterN[Future[Rep]]
  )(implicit ev: Req => HttpRequest): Service[Req, Rep] = routerOfServiceToService(r.map { f =>
    new Service[Req, Rep] {
      def apply(req: Req) = f
    }
  })

  implicit def intToMatcher(i: Int): Router0 = new Matcher(i.toString)
  implicit def stringToMatcher(s: String): Router0 = new Matcher(s)
  implicit def booleanToMather(b: Boolean): Router0 = new Matcher(b.toString)

  /**
   * A router that extracts some value of the type `A` from the given route.
   */
  trait RouterN[+A] { self =>

    /**
     * Extracts some value of type `A` from the given `route`. In case of
     * success it returns `Some` tuple of the _rest_ of the route and the fetched
     * _value_. In case of failure it returns `None`.
     */
    def apply(route: Route): Option[(Route, A)]

    /**
     * Maps this router to the given function `fn`.
     */
    def map[B](fn: A => B): RouterN[B] = new RouterN[B] {
      def apply(route: Route): Option[(Route, B)] = for {
        (r, a) <- self(route)
      } yield (r, fn(a))
      override def toString = self.toString
    }

    /**
     * Maps the router to the given function `fn`. If the given function `None`
     * the resulting router will also return `None`.
     */
    def maybeMap[B](fn: A => Option[B]): RouterN[B] = new RouterN[B] {
      def apply(route: Route): Option[(Route, B)] = for {
        (r, a) <- self(route)
        b <- fn(a)
      } yield (r, b)
      override def toString = self.toString
    }

    /**
     * Flat-maps this router to the given function `fn`.
     */
    def flatMap[B](fn: A => RouterN[B]): RouterN[B] = new RouterN[B] {
      def apply(route: Route): Option[(Route, B)] = for {
        (r, a) <- self(route)
        (rr, b) <- fn(a)(r)
      } yield (rr, b)
      override def toString = self.toString
    }

    /**
     * Flat-maps this router to the given [[Router0]].
     */
    def flatMap(rm: => Router0): RouterN[A] = new RouterN[A] {
      def apply(route: Route): Option[(Route, A)] = for {
        (r, a) <- self(route)
        rr <- rm(r)
      } yield (rr, a)

      override def toString = s"${self.toString}/${rm.toString}"
    }

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed if either this or `that` routers are succeed.
     *
     * Router composition via `orElse` operator happens in a _greedy_ manner: it
     * minimizes the output route tail. Thus, if both of the routers can handle
     * the given `route` the router is being chosen is that which eats more.
     */
    def orElse[B >: A](that: RouterN[B]): RouterN[B] = new RouterN[B] {
      def apply(route: Route): Option[(Route, B)] = (self(route), that(route)) match {
        case (aa @ Some((a, _)), bb @ Some((b, _))) =>
          if (a.length < b.length) aa else bb
        case (a, b) => a orElse b
      }

      override def toString = s"(${self.toString}|${that.toString})"
    }

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed only if both this and `that` routers are succeed.
     */
    def /[B](that: RouterN[B]): RouterN[A / B] = new RouterN[A / B] {
      val ab = for { a <- self; b <- that } yield new /(a, b)
      def apply(route: Route): Option[(Route, /[A, B])] = ab(route)
      override def toString = s"${self.toString}/${that.toString}"
    }

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed only if both this and `that` routers are succeed.
     */
    def /(that: Router0): RouterN[A] =
      this flatMap that

    /**
     * Maps this router to the given function `fn`.
     */
    def />[B](fn: A => B): RouterN[B] =
      this map fn

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed if either this or `that` routers are succeed.
     */
    def |[B >: A](that: RouterN[B]): RouterN[B] =
      this orElse that

    // A workaround for https://issues.scala-lang.org/browse/SI-1336
    def withFilter(p: A => Boolean) = self
  }

  /**
   * A router that match the given route to some predicate.
   */
  trait Router0 { self =>

    /**
     * Matches the given `route` to some predicate and returns `Some` of the
     * _rest_ of the route in case of success or `None` otherwise.
     */
    def apply(route: Route): Option[Route]

    /**
     * Maps this router to the given value `a`.
     */
    def map[A](a: => A): RouterN[A] = new RouterN[A] {
      def apply(route: Route): Option[(Route, A)] = for {
        r <- self(route)
      } yield (r, a)
      override def toString = self.toString
    }

    /**
     * Flat-maps this router to the given [[RouterN]].
     */
    def flatMap[A](re: => RouterN[A]): RouterN[A] = new RouterN[A] {
      def apply(route: Route): Option[(Route, A)] =
        self(route) flatMap { r => re(r) }
      override def toString = s"${self.toString}/${re.toString}"
    }

    /**
     * Flat-maps this router to the given `rm` router.
     */
    def flatMap(rm: => Router0): Router0 = new Router0 {
      def apply(route: Route): Option[Route] =
        self(route) flatMap { r => rm(r) }
      override def toString = s"${self.toString}/${rm.toString}"
    }

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed if either this or `that` routers are succeed.
     *
     * Router composition via `orElse` operator happens in a _greedy_ manner: it
     * minimizes the output route tail. Thus, if both of the routers can handle
     * the given `route` the router is being chosen is that which eats more.
     */
    def orElse(that: Router0): Router0 = new Router0 {
      def apply(route: Route): Option[Route] = (self(route), that(route)) match {
        case (aa @ Some(a), bb @ Some(b)) =>
          if (a.length < b.length) aa else bb
        case (a, b) => a orElse b
      }

      override def toString = s"(${self.toString}|${that.toString})"
    }

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed only if both this and `that` routers are succeed.
     */
    def /(that: Router0): Router0 =
      this flatMap that

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed only if both this and `that` routers are succeed.
     */
    def /[A](that: RouterN[A]): RouterN[A] =
      this flatMap that

    /**
     * Maps this router to some value.
     */
    def />[A](a: => A): RouterN[A] =
      this map a

    /**
     * Sequentially composes this router with the given `that` router. The resulting
     * router will succeed if either this or `that` routers are succeed.
     */
    def |(that: Router0): Router0 =
      this orElse that
  }

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
   * A [[RouterN]] that extracts a path token.
   */
  object PathTokenExtractor extends RouterN[String] {
    override def apply(route: Route): Option[(Route, String)] = for {
      PathToken(ss) <- route.headOption
    } yield (route.tail, ss)
  }

  /**
   * An universal extractor that extracts some value of type `A` if
   * it's possible to fetch the value from the string.
   */
  case class Extractor[A](name: String, f: String => Option[A]) extends RouterN[A] {
    def apply(route: Route): Option[(Route, A)] = PathTokenExtractor.maybeMap(f)(route)
    def apply(n: String): Extractor[A] = copy(name = n)
    override def toString = s":$name"
  }

  /**
   * A router that matches the given HTTP method `m` in the route.
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
   * A [[Router0]] that skips one route token.
   */
  object * extends Router0 {
    def apply(route: Route): Option[Route] = Some(route.tail)
    override def toString = "*"
  }

  /**
   * A router that extract an integer from the route.
   */
  object int extends Extractor("int", stringToSomeValue(_.toInt))

  /**
   * A router that extract a long value from the route.
   */
  object long extends Extractor("long", stringToSomeValue(_.toLong))

  /**
   * A router that extract a string value from the route.
   */
  object string extends Extractor("string", Some(_))

  /**
   * A router that extract a boolean value from the route.
   */
  object boolean extends Extractor("boolean", stringToSomeValue(_.toBoolean))
}
