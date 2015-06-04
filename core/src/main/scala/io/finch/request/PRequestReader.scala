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
import scala.reflect.ClassTag
import shapeless._
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler


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

object PRequestReader {
  private[this] def notParsed[A](rr: PRequestReader[_, _], tag: ClassTag[_]): PartialFunction[Throwable, Try[A]] = {
    case exc => Throw[A](NotParsed(rr.item, tag, exc))
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[String]` to perform a type conversion based
   * on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when type conversion fails.
   */
  implicit class StringReaderOps[R](val rr: PRequestReader[R, String]) extends AnyVal {
    def as[A](implicit decoder: DecodeRequest[A], tag: ClassTag[A]): PRequestReader[R, A] = rr.embedFlatMap { value =>
      Future.const(decoder(value).rescue(notParsed[A](rr, tag)))
    }
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[Option[String]]` to perform a type conversion
   * based on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails. It will succeed if the
   * result is empty or type conversion succeeds.
   */
  implicit class StringOptionReaderOps[R](val rr: PRequestReader[R, Option[String]]) extends AnyVal {
    def as[A](implicit decoder: DecodeRequest[A], tag: ClassTag[A]): PRequestReader[R, Option[A]] = rr.embedFlatMap {
      case Some(value) => Future.const(decoder(value).rescue(notParsed[A](rr, tag)) map (Some(_)))
      case None => Future.None
    }

    private[request] def noneIfEmpty: PRequestReader[R, Option[String]] = rr.map {
      case Some(value) if value.isEmpty => None
      case other => other
    }
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[Seq[String]]` to perform a type conversion
   * based on an implicit ''DecodeRequest[A]'' which must be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails on one or more of the
   * elements in the `Seq`. It will succeed if the result is empty or type conversion succeeds for all elements.
   */
  implicit class StringSeqReaderOps[R](val rr: PRequestReader[R, Seq[String]]) {

    /* IMPLEMENTATION NOTE: This implicit class should extend AnyVal like all the other ones, to avoid instance creation
     * for each invocation of the extension method. However, this let's us run into a compiler bug when we compile for
     * Scala 2.10: https://issues.scala-lang.org/browse/SI-8018. The bug is caused by the combination of four things:
     * 1) an implicit class, 2) extending AnyVal, 3) wrapping a class with type parameters, 4) a partial function in the
     * body. 2) is the only thing we can easily remove here, otherwise we'd need to move the body of the method
     * somewhere else. Once we drop support for Scala 2.10, this class can safely extends AnyVal.
     */

    def as[A](implicit decoder: DecodeRequest[A], tag: ClassTag[A]): PRequestReader[R, Seq[A]] =
      rr.embedFlatMap { items =>
        val converted = items map (decoder(_))
        if (converted.forall(_.isReturn)) converted.map(_.get).toFuture
        else RequestErrors(converted collect { case Throw(e) => NotParsed(rr.item, tag, e) }).toFutureException[Seq[A]]
      }
  }

  /**
   * Implicit conversion that adds convenience methods to readers for optional values.
   */
  implicit class OptionReaderOps[R, A](val rr: PRequestReader[R, Option[A]]) extends AnyVal {
    private[request] def failIfNone: PRequestReader[R, A] = rr.embedFlatMap {
      case Some(value) => value.toFuture
      case None => NotPresent(rr.item).toFutureException[A]
    }

    /**
     * If reader is empty it will return provided default value
     */
    def withDefault[B >: A](default: => B): PRequestReader[R, B] = rr.map(_.getOrElse(default))

    /**
     * If reader is empty it will return provided alternative
     */
    def orElse[B >: A](alternative: => Option[B]): PRequestReader[R, Option[B]] = rr.map(_.orElse(alternative))
  }

  /**
   * Implicit class that provides `::` and other operations on any request reader that returns a
   * [[shapeless.HList]].
   *
   * See the implementation note on [[StringSeqReaderOps]] for a discussion of why this is not
   * currently a value class.
   */
  final implicit class HListReaderOps[R, L <: HList](val self: PRequestReader[R, L]) {

    /**
     * Composes this request reader with the given `that` request reader.
     */
    def ::[S, A](that: PRequestReader[S, A])(implicit ev: S %> R): PRequestReader[S, A :: L] =
      new PRequestReader[S, A :: L] {
        val item = MultipleItems
        def apply(req: S): Future[A :: L] =
          Future.join(that(req).liftToTry, self(ev(req)).liftToTry).flatMap {
            case (Return(a), Return(l)) => (a :: l).toFuture
            case (Throw(a), Throw(l)) => collectExceptions(a, l).toFutureException[A :: L]
            case (Throw(e), _) => e.toFutureException[A :: L]
            case (_, Throw(e)) => e.toFutureException[A :: L]
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
     * Converts this request reader to one that returns any type with this [[shapeless.HList]] as
     * its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, L]): PRequestReader[R, A] = self.map(gen.from)

    /**
     * Converts this request reader to one that returns a tuple with the same types as this
     * [[shapeless.HList]].
     *
     * Note that this will fail at compile time if this this [[shapeless.HList]] contains more than
     * 22 elements.
     */
    def asTuple(implicit tupler: Tupler[L]): PRequestReader[R, tupler.Out] = self.map(tupler(_))

    /**
     * Applies a `FunctionN` with the appropriate arity and types and a `Future` return type to
     * the elements of this [[shapeless.HList]].
     */
    def ~~>[F, I, FI](fn: F)(
      implicit ftp: FnToProduct.Aux[F, L => FI], ev: FI <:< Future[I]
    ): PRequestReader[R, I] = self.embedFlatMap(value => ev(ftp(fn)(value)))

    /**
     * Applies a `FunctionN` with the appropriate arity and types to the elements of this
     * [[shapeless.HList]].
     */
    def ~>[F, I](fn: F)(implicit ftp: FnToProduct.Aux[F, L => I]): PRequestReader[R, I] =
      self.map(ftp(fn))
  }

  /**
   * Implicit class that provides `::` on any request reader to support building [[shapeless.HList]]
   * request readers.
   */
  final implicit class ValueReaderOps[R, B](val self: PRequestReader[R, B]) extends AnyVal {

    /**
     * Lift this request reader into a singleton [[shapeless.HList]] and compose it with the given
     * `that` request reader.
     */
    def ::[S, A](that: PRequestReader[S, A])(implicit ev: S %> R): PRequestReader[S, A :: B :: HNil] =
      that :: self.map(_ :: HNil)

    /**
     * Converts this request reader to one that returns any type with `B :: HNil` as
     * its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, B :: HNil]): PRequestReader[R, A] = self.map { value =>
      gen.from(value :: HNil)
    }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of two arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow2[R, A, B](val rr: PRequestReader[R, A ~ B]) extends AnyVal {
    def ~~>[C](fn: (A, B) => Future[C]): PRequestReader[R, C] =
      rr.embedFlatMap { case (a ~ b) => fn(a, b) }

    def ~>[C](fn: (A, B) => C): PRequestReader[R, C] =
      rr.map { case (a ~ b) => fn(a, b) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of three arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow3[R, A, B, C](val rr: PRequestReader[R, A ~ B ~ C]) extends AnyVal {
    def ~~>[D](fn: (A, B, C) => Future[D]): PRequestReader[R, D] =
      rr.embedFlatMap { case (a ~ b ~ c) => fn(a, b, c) }

    def ~>[D](fn: (A, B, C) => D): PRequestReader[R, D] =
      rr.map { case (a ~ b ~ c) => fn(a, b, c) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of four arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow4[R, A, B, C, D](val rr: PRequestReader[R, A ~ B ~ C ~ D]) extends AnyVal {
    def ~~>[E](fn: (A, B, C, D) => Future[E]): PRequestReader[R, E] =
      rr.embedFlatMap { case (a ~ b ~ c ~ d) => fn(a, b, c, d) }

    def ~>[E](fn: (A, B, C, D) => E): PRequestReader[R, E] =
      rr.map { case (a ~ b ~ c ~ d) => fn(a, b, c, d) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of five arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow5[R, A, B, C, D, E](val rr: PRequestReader[R, A ~ B ~ C ~ D ~ E]) extends AnyVal {
    def ~~>[F](fn: (A, B, C, D, E) => Future[F]): PRequestReader[R, F] =
      rr.embedFlatMap { case (a ~ b ~ c ~ d ~ e) => fn(a, b, c, d, e) }

    def ~>[F](fn: (A, B, C, D, E) => F): PRequestReader[R, F] =
      rr.map { case (a ~ b ~ c ~ d ~ e) => fn(a, b, c, d, e) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of six arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow6[R, A, B, C, D, E, F](val rr: PRequestReader[R, A ~ B ~ C ~ D ~ E ~ F]) extends AnyVal {
    def ~~>[G](fn: (A, B, C, D, E, F) => Future[G]): PRequestReader[R, G] =
      rr.embedFlatMap { case (a ~ b ~ c ~ d ~ e ~ f) => fn(a, b, c, d, e, f) }

    def ~>[G](fn: (A, B, C, D, E, F) => G): PRequestReader[R, G] =
      rr.map { case (a ~ b ~ c ~ d ~ e ~ f) => fn(a, b, c, d, e, f) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of seven arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow7[R, A, B, C, D, E, F, G](val rr: PRequestReader[R, A ~ B ~ C ~ D ~ E ~ F ~ G]) extends AnyVal {
    def ~~>[H](fn: (A, B, C, D, E, F, G) => Future[H]): PRequestReader[R, H] =
      rr.embedFlatMap { case (a ~ b ~ c ~ d ~ e ~ f ~ g) => fn(a, b, c, d, e, f, g) }

    def ~>[H](fn: (A, B, C, D, E, F, G) => H): PRequestReader[R, H] =
      rr.map { case (a ~ b ~ c ~ d ~ e ~ f ~ g) => fn(a, b, c, d, e, f, g) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with function of eight arguments.
   */
  @deprecated("~ is deprecated in favor of HList-based composition", "0.7.0")
  implicit class RrArrow8[R, A, B, C, D, E, F, G, H](
    val rr: PRequestReader[R, A ~ B ~ C ~ D ~ E ~ F ~ G ~ H]
  ) extends AnyVal {
    def ~~>[I](fn: (A, B, C, D, E, F, G, H) => Future[I]): PRequestReader[R, I] =
      rr.embedFlatMap { case (a ~ b ~ c ~ d ~ e ~ f ~ g ~ h) => fn(a, b, c, d, e, f, g, h) }

    def ~>[I](fn: (A, B, C, D, E, F, G, H) => I): PRequestReader[R, I] =
      rr.map { case (a ~ b ~ c ~ d ~ e ~ f ~ g ~ h) => fn(a, b, c, d, e, f, g, h) }
  }

  /**
   * Adds a `~>` and `~~>` compositors to `RequestReader` to compose it with functions of one argument.
   */
  implicit class RrArrow1[R, A](rr: PRequestReader[R, A]) {
    def ~~>[B](fn: A => Future[B]): PRequestReader[R, B] =
      rr.embedFlatMap(fn)

    def ~>[B](fn: A => B): PRequestReader[R, B] =
      rr.map(fn)
  }
}
