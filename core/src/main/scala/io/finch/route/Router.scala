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
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Future
import io.finch._
import io.finch.request._
import io.finch.response._
import shapeless._
import shapeless.ops.adjoin.Adjoin
import shapeless.ops.function.FnToProduct


/**
 * A router that extracts some value of the type `A` from the given route.
 */
trait Router[A] { self =>
  import Router._

  /**
   * Extracts some value of type `A` from the given `input`.
   */
  def apply(input: Input): Future[Output[A]]

  /**
   * Attempts to match a route, but only returns any unmatched elements, not the value.
   */
  private[route] def exec(input: Input): Future[Option[Input]] = apply(input).map {
    case Output.Accepted(r, a) => Some(r)
    case Output.Dropped => None
  }

  /**
   * Maps this router to the given function `A => B`.
   */
  def map[B](fn: A => B): Router[B] = new Router[B] {
    def apply(input: Input): Future[Output[B]] =
      self(input).map(_.map(fn))

    override def toString = self.toString
  }

  /**
   * Flat-maps the router to the given function `A => Future[B]`. If the given function `None` the resulting router will
   * also return `None`.
   */
  def embedFlatMap[B](fn: A => Future[B]): Router[B] = new Router[B] {
    def apply(input: Input): Future[Output[B]] =
      self(input).flatMap {
        case Output.Accepted(r, a) => fn(a).map(b => Output.accepted[B](r, b))
        case Output.Dropped => Output.dropped[B].toFuture
      }

    override def toString = self.toString
  }

  /**
   * Flat-maps this router to the given function `A => Router[B]`.
   */
  def flatMap[B](fn: A => Router[B]): Router[B] = new Router[B] {
    def apply(input: Input): Future[Output[B]] =
      self(input).flatMap {
        case Output.Accepted(r, a) => fn(a)(r)
        case Output.Dropped => Output.dropped[B].toFuture
      }

    override def toString = self.toString
  }

  /**
   * Composes this router with the given `that` router. The resulting router will succeed only if both this and `that`
   * routers are succeed.
   */
  def /[B](that: Router[B])(implicit adjoin: PairAdjoin[A, B]): Router[adjoin.Out] =
    new Router[adjoin.Out] {
      val ab = for { a <- self; b <- that } yield adjoin(a, b)
      def apply(input: Input): Future[Output[adjoin.Out]] = ab(input)

      override def toString = s"${self.toString}/${that.toString}"
    }

  /**
   * Composes this router with the given [[RequestReader]].
   */
  def ?[B](that: RequestReader[B])(implicit adjoin: PairAdjoin[A, B]): Router[adjoin.Out] =
    new Router[adjoin.Out] {
      def apply(input: Input): Future[Output[adjoin.Out]] =
        self(input).flatMap {
          case Output.Accepted(r, a) => that(input.request).map(b => Output.accepted[adjoin.Out](r, adjoin(a, b)))
          case Output.Dropped => Output.dropped[adjoin.Out].toFuture
        }

      override def toString = s"${self.toString}?${that.toString}"
    }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   */
  def |[B >: A](that: Router[B]): Router[B] = new Router[B] {
    def apply(input: Input): Future[Output[B]] =
      self(input).flatMap {
        case Output.Dropped => that(input)
        case accepted => accepted.toFuture
      }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  // A workaround for https://issues.scala-lang.org/browse/SI-1336
  def withFilter(p: A => Boolean): Router[A] = self

  /**
   * Compose this router with another in such a way that coproducts are flattened.
   */
  def :+:[B](that: Router[B])(implicit adjoin: Adjoin[B :+: A :+: CNil]): Router[adjoin.Out] =
    that.map(b => adjoin(Inl[B, A :+: CNil](b))) |
    self.map(a => adjoin(Inr[B, A :+: CNil](Inl[A, CNil](a))))

  /**
   * Converts this router to a Finagle service from a request-like type `R` to a [[Response]].
   */
  def toService[R: ToRequest](implicit ts: ToService[R, A]): Service[R, Response] = ts(this)
}

/**
 * Provides extension methods for [[Router]] to support coproduct and path
 * syntax.
 */
object Router {

  /**
   * An input for [[Router]].
   */
  final case class Input(request: Request, path: Seq[String]) {
    def headOption: Option[String] = path.headOption
    def drop(n: Int): Input = copy(path = path.drop(n))
    def isEmpty: Boolean = path.isEmpty
  }

  /**
   * Creates an input for [[Router]] from [[Request]].
   */
  def Input(req: Request): Input = Input(req, req.path.split("/").toList.drop(1))

  /**
   * An output from [[Router]] that carries some value of type [[A]].
   */
  sealed trait Output[+A] {
    def map[B](fn: A => B): Output[B]
    def orElse[B >: A](that: Output[B]): Output[B]
  }

  object Output {

    /**
     * Creates the [[Router]]s [[Output]] identifying that request was accepted.
     */
    def accepted[A](r: Input, v: A): Output[A] = Accepted(r, v)

    /**
     * Creates the [[Router]]s [[Output]] identifying that request was dropped.
     */
    def dropped[A]: Output[A] = Dropped

    /**
     * Creates [[Output]] from [[Option]].
     */
    def fromOption[A](r: Input, o: Option[A]): Output[A] = o match {
      case Some(a) => accepted[A](r, a)
      case None => dropped[A]
    }

    /**
     * An [[Output]] identifying that request was accepted.
     */
    final case class Accepted[+A](remainder: Input, value: A) extends Output[A] {
      def map[B](fn: A => B): Output[B] = Accepted(remainder, fn(value))
      def orElse[B >: A](that: Output[B]): Output[B] = this
    }

    /**
     * An [[Output]] identifying that request was dropped.
     */
    case object Dropped extends Output[Nothing] {
      def map[B](fn: Nothing => B): Output[B] = this
      def orElse[B >: Nothing](that: Output[B]): Output[B] = that
    }
  }

  /**
   * Creates a [[Router]] from the given function `Input => Output[A]`.
   */
  def apply[A](fn: Input => Output[A]): Router[A] = new Router[A] {
    def apply(input: Input): Future[Output[A]] = fn(input).toFuture
  }

  /**
   * An implicit conversion that turns any endpoint with an output type that can be converted into a response into a
   * service that returns responses.
   */
  implicit def endpointToResponse[A, B](e: Endpoint[A, B])(implicit
    encoder: EncodeResponse[B]
  ): Endpoint[A, Response] = e.map { service =>
    new Service[A, Response] {
      def apply(a: A): Future[Response] = service(a).map(b => Ok(encoder(b)))
    }
  }

  /**
   * Implicitly converts the given `Router[Service[_, _]]` into a service.
   */
  implicit def endpointToService[Req, Rep](
    router: Router[Service[Req, Rep]]
  )(implicit ev: Req => Request): Service[Req, Rep] = new Service[Req, Rep] {
    def apply(req: Req): Future[Rep] = router(Input(req)).flatMap {
      case Output.Accepted(r, s) if r.isEmpty => s(req)
      case _ => RouteNotFound(s"${req.method.toString.toUpperCase} ${req.path}").toFutureException[Rep]
    }
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of one argument.
   */
  implicit class RArrow1[A](r: Router[A]) {
    def />[B](fn: A => B): Router[B] = r.map(fn)
    def />>[B](fn: A => Future[B]): Router[B] = r.embedFlatMap(fn)
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with values.
   */
  implicit class RArrow0(r: Router0) {
    def />[B](v: => B): Router[B] = r.map(_ => v)
    def />>[B](v: => Future[B]): Router[B] = r.embedFlatMap(_ => v)
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of two arguments.
   */
  implicit class RArrow2[A, B](r: Router2[A, B]) {
    def />[C](fn: (A, B) => C): Router[C] = r.map {
      case a :: b :: HNil => fn(a, b)
    }

    def />>[C](fn: (A, B) => Future[C]): Router[C] = r.embedFlatMap {
      case a :: b :: HNil => fn(a, b)
    }
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of three arguments.
   */
  implicit class RArrow3[A, B, C](r: Router3[A, B, C]) {
    def />[D](fn: (A, B, C) => D): Router[D] = r.map {
      case a :: b :: c :: HNil => fn(a, b, c)
    }

    def />>[D](fn: (A, B, C) => Future[D]): Router[D] = r.embedFlatMap {
      case a :: b :: c :: HNil => fn(a, b, c)
    }
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of N arguments.
   */
  implicit class RArrowN[L <: HList](r: Router[L]) {
    def />[F, I](fn: F)(implicit ftp: FnToProduct.Aux[F, L => I]): Router[I] =
      r.map(ftp(fn))

    def />>[F, I, FI](fn: F)(
      implicit ftp: FnToProduct.Aux[F, L => FI], ev: FI <:< Future[I]
    ): Router[I] = r.embedFlatMap(value => ev(ftp(fn)(value)))
  }
}
