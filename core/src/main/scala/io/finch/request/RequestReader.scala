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
 * Contributor(s):
 * Ben Whitehead
 * Ryan Plessner
 * Pedro Viegas
 * Jens Halm
 */

package io.finch.request

import com.twitter.util.{Future, Return, Throw, Try}
import io.finch._
import io.finch.request.items._

/**
 * A polymorphic request reader (a reader monad) that reads a [[Future]] of `A` from the request of type `R`.
 */
trait PRequestReader[R, A] { self =>

  /**
   * A [[io.finch.request.items.RequestItem RequestItem]] read by this request reader.
   */
  def item: RequestItem

  /**
   * Reads the data from given request `req`.
   *
   * @param req the request to read
   */
  def apply(req: R): Future[A]

  /**
   * Flat-maps this request reader to the given function `A => PRequestReader[R, B]`.
   */
  def flatMap[S, B](fn: A => PRequestReader[S, B])(
    implicit ev: R %> S
  ): PRequestReader[R, B] = new PRequestReader[R, B] {
    val item = MultipleItems
    def apply(req: R): Future[B] = self(req) flatMap { fn(_)(ev(req)) }
  }

  /**
   * Maps this request reader to the given function `A => B`.
   */
  def map[B](fn: A => B): PRequestReader[R, B] = new PRequestReader[R, B] {
    val item = self.item
    def apply(req: R): Future[B] = self(req) map fn
  }

  /**
   * Flat-maps this request reader to the given function `A => Future[B]`.
   */
  def embedFlatMap[B](fn: A => Future[B]): PRequestReader[R, B] = new PRequestReader[R, B] {
    val item = self.item
    def apply(req: R): Future[B] = self(req) flatMap fn
  }

   /**
   * Composes this request reader with the given `that` request reader.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  def ~[S, B](that: PRequestReader[S, B])(
    implicit ev: R %> S
  ): PRequestReader[R, A ~ B] = new PRequestReader[R, A ~ B] {
    val item = MultipleItems
    def apply(req: R): Future[A ~ B] = Future.join(self(req).liftToTry, that(ev(req)).liftToTry).flatMap {
      case (Return(a), Return(b)) => new ~(a, b).toFuture
      case (Throw(a), Throw(b)) => collectExceptions(a, b).toFutureException[A ~ B]
      case (Throw(e), _) => e.toFutureException[A ~ B]
      case (_, Throw(e)) => e.toFutureException[A ~ B]
    }

    def collectExceptions(a: Throwable, b: Throwable): RequestErrors = {
      def collect(e: Throwable): Seq[Throwable] = e match {
        case RequestErrors(errors) => errors
        case other => Seq(other)
      }

      RequestErrors(collect(a) ++ collect(b))
    }
  }

  /**
   * Applies the given filter `p` to this request reader.
   */
  def withFilter(p: A => Boolean): PRequestReader[R, A] = self.should("not fail validation")(p)

  /**
   * Lifts this request reader into one that always succeeds, with an empty option representing failure.
   */
  def lift: PRequestReader[R, Option[A]] = new PRequestReader[R, Option[A]] {
    val item = self.item
    def apply(req: R): Future[Option[A]] = self(req).liftToTry.map(_.toOption)
  }

  /**
   * Validates the result of this request reader using a `predicate`. The rule is used for error reporting.
   *
   * @param rule text describing the rule being validated
   * @param predicate returns true if the data is valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def should(rule: String)(predicate: A => Boolean): PRequestReader[R, A] = embedFlatMap { a =>
    if (predicate(a)) a.toFuture
    else NotValid(self.item, "should " + rule).toFutureException[A]
  }

  /**
   * Validates the result of this request reader using a `predicate`. The rule is used for error reporting.
   *
   * @param rule text describing the rule being validated
   * @param predicate returns false if the data is valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def shouldNot(rule: String)(predicate: A => Boolean): PRequestReader[R, A] = should(s"not $rule.")(x => !predicate(x))

  /**
   * Validates the result of this request reader using a predefined `rule`. This method allows for rules to be reused
   * across multiple request readers.
   *
   * @param rule the predefined [[io.finch.request.ValidationRule ValidationRule]] that will return true if the data is
   *             valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def should(rule: ValidationRule[A]): PRequestReader[R, A] = should(rule.description)(rule.apply)

  /**
   * Validates the result of this request reader using a predefined `rule`. This method allows for rules to be reused
   * across multiple request readers.
   *
   * @param rule the predefined [[io.finch.request.ValidationRule ValidationRule]] that will return false if the data is
   *             valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def shouldNot(rule: ValidationRule[A]): PRequestReader[R, A] = shouldNot(rule.description)(rule.apply)
}

/**
 * Convenience methods for creating new reader instances.
 */
object RequestReader {

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always succeeds, producing the specified value.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always succeeds, producing the specified value
   */
  def value[A](value: A): RequestReader[A] = const[A](value.toFuture)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always fails, producing the specified
   * exception.
   *
   * @param exc the exception the new reader should produce
   * @return a new reader that always fails, producing the specified exception
   */
  def exception[A](exc: Throwable): RequestReader[A] = const[A](exc.toFutureException[A])

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always produces the specified value. It will
   * succeed if the given `Future` succeeds and fail if the `Future` fails.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always produces the specified value
   */
  def const[A](value: Future[A]): RequestReader[A] = embed[HttpRequest, A](MultipleItems)(_ => value)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that reads the result from the request.
   *
   * @param f the function to apply to the request
   * @return a new reader that reads the result from the request
   */
  def apply[R, A](f: R => A): PRequestReader[R, A] = embed[R, A](MultipleItems)(f(_).toFuture)

  private[request] def embed[R, A](i: RequestItem)(f: R => Future[A]): PRequestReader[R, A] =
    new PRequestReader[R, A] {
      val item = i
      def apply(req: R): Future[A] = f(req)
    }

  import scala.reflect.ClassTag
  import shapeless._, labelled.{FieldType, field}

  class GenericDerivation[A] {
    def fromParams[Repr <: HList](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      fp: FromParams[Repr]
    ): RequestReader[A] = fp.reader.map(gen.from)
  }

  trait FromParams[L <: HList] {
    def reader: RequestReader[L]
  }

  object FromParams {
    implicit val hnilFromParams: FromParams[HNil] = new FromParams[HNil] {
      def reader: RequestReader[HNil] = value(HNil)
    }

    implicit def hconsFromParams[HK <: Symbol, HV, T <: HList](implicit
      dh: DecodeRequest[HV],
      ct: ClassTag[HV],
      key: Witness.Aux[HK],
      fpt: FromParams[T]
    ): FromParams[FieldType[HK, HV] :: T] = new FromParams[FieldType[HK, HV] :: T] {
      def reader: RequestReader[FieldType[HK, HV] :: T] =
        param(key.value.name).as(dh, ct).map(field[HK](_)) :: fpt.reader
    }
  }

  def to[A]: GenericDerivation[A] = new GenericDerivation[A]
}
