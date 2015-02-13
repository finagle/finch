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
package object route {

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
   * Implicitly converts the given `Router[Service[_, _]]` into a service.
   */
  implicit def endpointToService[Req, Rep](
    r: RouterN[Service[Req, Rep]]
  )(implicit ev: Req => HttpRequest): Service[Req, Rep] = new Service[Req, Rep] {

    private def requestToRoute(req: Req)(implicit ev: Req => HttpRequest): Route =
      MethodToken(req.method) :: (req.path.split("/").toList.tail map PathToken)

    def apply(req: Req): Future[Rep] = {
      val path = requestToRoute(req)
      r(path) match {
        case Some((Nil, service)) => service(req)
        case _ => RouteNotFound(s"${req.method.toString.toUpperCase} ${req.path}").toFutureException
      }
    }
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
    def apply(n: String): Extractor[A] = copy(name = n)
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
    def apply(route: Route): Option[Route] = Some(route.tail)
    override def toString = "*"
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
