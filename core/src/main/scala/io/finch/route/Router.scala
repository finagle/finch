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
import shapeless.ops.function.FnToProduct
import shapeless.ops.adjoin.Adjoin


/**
 * A router that extracts some value of the type `A` from the given route.
 */
trait Router[A] { self =>

  /**
   * Extracts some value of type `A` from the given `input`. In case of success it returns `Some` tuple of the _rest_ of
   * the route and the fetched _value_. In case of failure it returns `None`.
   */
  def apply(input: RouterInput): Option[(RouterInput, A)]

  /**
   * Attempts to match a route, but only returns any unmatched elements, not the value.
   */
  private[route] def exec(input: RouterInput): Option[RouterInput] = apply(input).map(_._1)

  /**
   * Maps this router to the given function `A => B`.
   */
  def map[B](fn: A => B): Router[B] = new Router[B] {
    def apply(input: RouterInput): Option[(RouterInput, B)] = for {
      (r, a) <- self(input)
    } yield (r, fn(a))
    override def toString = self.toString
  }

  /**
   * Flat-maps the router to the given function `A => Option[B]`. If the given function `None` the resulting router will
   * also return `None`.
   */
  def embedFlatMap[B](fn: A => Option[B]): Router[B] = new Router[B] {
    def apply(input: RouterInput): Option[(RouterInput, B)] = for {
      (r, a) <- self(input)
      b <- fn(a)
    } yield (r, b)
    override def toString = self.toString
  }

  /**
   * Flat-maps this router to the given function `A => Router[B]`.
   */
  def flatMap[B](fn: A => Router[B]): Router[B] = new Router[B] {
    def apply(input: RouterInput): Option[(RouterInput, B)] = for {
      (r, a) <- self(input)
      (rr, b) <- fn(a)(r)
    } yield (rr, b)
    override def toString = self.toString
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def andThen[B](that: Router[B])(implicit adjoin: PairAdjoin[A, B]): Router[adjoin.Out] =
    new Router[adjoin.Out] {
      val ab = for { a <- self; b <- that } yield adjoin(a, b)
      def apply(route: RouterInput): Option[(RouterInput, adjoin.Out)] = ab(route)
      override def toString = s"${self.toString}/${that.toString}"
    }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   *
   * Router composition via `orElse` operator happens in a _greedy_ manner: it minimizes the output route tail. Thus, if
   * both of the routers can handle the given `route` the router is being chosen is that which eats more.
   */
  def orElse[B >: A](that: Router[B]): Router[B] = new Router[B] {
    def apply(input: RouterInput): Option[(RouterInput, B)] = (self(input), that(input)) match {
      case (aa @ Some((a, _)), bb @ Some((b, _))) =>
        if (a.path.length <= b.path.length) aa else bb
      case (a, b) => a orElse b
    }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /[B](that: Router[B])(implicit adjoin: PairAdjoin[A, B]): Router[adjoin.Out] =
    this andThen that

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def |[B >: A](that: Router[B]): Router[B] =
    this orElse that

  // A workaround for https://issues.scala-lang.org/browse/SI-1336
  def withFilter(p: A => Boolean): Router[A] = self

  /**
   * Compose this router with another in such a way that coproducts are flattened.
   */
  def :+:[B](that: Router[B])(implicit adjoin: Adjoin[B :+: A :+: CNil]): Router[adjoin.Out] =
    that.map(b => adjoin(Inl(b))) orElse map(a => adjoin(Inr(Inl(a))))

  /**
   * Converts this router to a Finagle service from a request-like type `R` to a [[HttpResponse]].
   */
  def toService[R: ToRequest](implicit ts: ToService[R, A]): Service[R, HttpResponse] = ts(this)
}

/**
 * Provides extension methods for [[Router]] to support coproduct and path
 * syntax.
 */
object Router {
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
    r: Router[Service[Req, Rep]]
  )(implicit ev: Req => HttpRequest): Service[Req, Rep] = new Service[Req, Rep] {
    def apply(req: Req): Future[Rep] = r(RouterInput(req)) match {
      case Some((input, service)) if input.path.isEmpty => service(req)
      case _ => RouteNotFound(s"${req.method.toString.toUpperCase} ${req.path}").toFutureException[Rep]
    }
  }

  /**
   * Add `/>` compositor to `Router` to compose it with function of one argument.
   */
  implicit class RArrow1[A](r: Router[A]) {
    def />[B](fn: A => B): Router[B] = r.map(fn)
  }

  /**
   * Add `/>` compositor to `Router` to compose it with values.
   */
  implicit class RArrow0(r: Router0) {
    def />[B](v: => B): Router[B] = r.map(_ => v)
  }

  /**
   * Add `/>` compositor to `Router` to compose it with function of two arguments.
   */
  implicit class RArrow2[A, B](r: Router2[A, B]) {
    def />[C](fn: (A, B) => C): Router[C] = r.map {
      case a :: b :: HNil => fn(a, b)
    }
  }

  /**
   * Add `/>` compositor to `Router` to compose it with function of three arguments.
   */
  implicit class RArrow3[A, B, C](r: Router3[A, B, C]) {
    def />[D](fn: (A, B, C) => D): Router[D] = r.map {
      case a :: b :: c :: HNil => fn(a, b, c)
    }
  }

  /**
   * Add `/>` compositor to `Router` to compose it with function of N arguments.
   */
  implicit class RArrowN[L <: HList](r: Router[L]) {
    def />[F, I](fn: F)(implicit ftp: FnToProduct.Aux[F, L => I]): Router[I] =
      r.map(ftp(fn))
  }
}
