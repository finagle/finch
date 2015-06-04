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

package io.finch.route

import com.twitter.finagle.Service
import com.twitter.util.Future
import io.finch._
import io.finch.request._
import io.finch.response._
import shapeless._

/**
 * A router that extracts some value of the type `A` from the given route.
 */
trait RouterN[+A] { self =>

  /**
   * Extracts some value of type `A` from the given `route`. In case of success it returns `Some` tuple of the _rest_ of
   * the route and the fetched _value_. In case of failure it returns `None`.
   */
  def apply(route: Route): Option[(Route, A)]

  /**
   * Maps this router to the given function `A => B`.
   */
  def map[B](fn: A => B): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = for {
      (r, a) <- self(route)
    } yield (r, fn(a))
    override def toString = self.toString
  }

  /**
   * Flat-maps the router to the given function `A => Option[B]`. If the given function `None` the resulting router will
   * also return `None`.
   */
  def embedFlatMap[B](fn: A => Option[B]): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = for {
      (r, a) <- self(route)
      b <- fn(a)
    } yield (r, b)
    override def toString = self.toString
  }

  /**
   * Flat-maps this router to the given function `A => RouterN[B]`.
   */
  def flatMap[B](fn: A => RouterN[B]): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = for {
      (r, a) <- self(route)
      (rr, b) <- fn(a)(r)
    } yield (rr, b)
    override def toString = self.toString
  }

  /**
   * Sequentially composes this router with the given `that` [[io.finch.route.Router0 Router0]].
   */
  def andThen(that: => Router0): RouterN[A] = new RouterN[A] {
    def apply(route: Route): Option[(Route, A)] = for {
      (r, a) <- self(route)
      rr <- that(r)
    } yield (rr, a)

    override def toString = s"${self.toString}/${that.toString}"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def andThen[B](that: RouterN[B]): RouterN[A / B] = new RouterN[A / B] {
    val ab = for { a <- self; b <- that } yield new /(a, b)
    def apply(route: Route): Option[(Route, /[A, B])] = ab(route)
    override def toString = s"${self.toString}/${that.toString}"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   *
   * Router composition via `orElse` operator happens in a _greedy_ manner: it minimizes the output route tail. Thus, if
   * both of the routers can handle the given `route` the router is being chosen is that which eats more.
   */
  def orElse[B >: A](that: RouterN[B]): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = (self(route), that(route)) match {
      case (aa @ Some((a, _)), bb @ Some((b, _))) =>
        if (a.length <= b.length) aa else bb
      case (a, b) => a orElse b
    }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /[B](that: RouterN[B]): RouterN[A / B] =
    this andThen that

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /(that: Router0): RouterN[A] =
    this andThen that

  // A workaround for https://issues.scala-lang.org/browse/SI-1336
  def withFilter(p: A => Boolean): RouterN[A] = self

  /**
   * Converts this router to a Finagle service from a request-like type `R` to a [[HttpResponse]].
   */
  def toService[R: ToRequest](implicit ts: ToService[R, A]): Service[R, HttpResponse] = ts(this)
}

/**
 * Provides extension methods for [[RouterN]] to support coproduct and path
 * syntax.
 */
object RouterN {
  /**
   * Add `/>` compositors to `RouterN` to compose it with function of one argument.
   */
  implicit class RArrow1[A](r: RouterN[A]) {
    def />[B](fn: A => B): RouterN[B] = r.map(fn)
    def |[B >: A](that: RouterN[B]): RouterN[B] = r orElse that
  }

  /**
   * An implicit conversion that turns any endpoint with an output type that can be converted into a response into a
   * service that returns responses.
   */
  implicit def endpointToHttpResponse[A, B](e: Endpoint[A, B])(implicit
    encoder: EncodeResponse[B]
  ): Endpoint[A, HttpResponse] = e.map { service =>
    new Service[A, HttpResponse] {
      def apply(a: A): Future[HttpResponse] = service(a).map(b => Ok(encoder(b)))
    }
  }

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
   * Implicit class that provides `:+:` and other operations on any coproduct router.
   */
  final implicit class CoproductRouterNOps[C <: Coproduct](self: RouterN[C]) {
    def :+:[A](that: RouterN[A]): RouterN[A :+: C] =
      new RouterN[A :+: C] {
        def apply(route: Route): Option[(Route, A :+: C)] =
          (that(route), self(route)) match {
            case (aa @ Some((ar, av)), cc @ Some((cr, cv))) =>
              if (ar.length <= cr.length) Some((ar, Inl(av))) else Some((cr, Inr(cv)))
            case (a, c) => a.map {
              case (r, v) => (r, Inl(v))
            } orElse c.map {
              case (r, v) => (r, Inr(v))
            }
          }

        override def toString = s"(${that.toString}|${self.toString})"
      }
  }

  /**
   * Implicit class that provides `:+:` on any router.
   */
  final implicit class ValueRouterNOps[B](self: RouterN[B]) {
    def :+:[A](that: RouterN[A]): RouterN[A :+: B :+: CNil] =
      new RouterN[A :+: B :+: CNil] {
        def apply(route: Route): Option[(Route, A :+: B :+: CNil)] =
          (that(route), self(route)) match {
            case (aa @ Some((ar, av)), bb @ Some((br, bv))) =>
              if (ar.length <= br.length) Some((ar, Inl(av))) else Some((br, Inr(Inl(bv))))
            case (a, b) => a.map {
              case (r, v) => (r, Inl(v))
            } orElse b.map {
              case (r, v) => (r, Inr(Inl(v)))
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
}
