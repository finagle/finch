package io.finch.request

import com.twitter.finagle.http.Request
import com.twitter.util.{Return, Throw, Try, Future}
import io.finch._
import io.finch.request.items._
import shapeless.ops.function.FnToProduct
import shapeless.ops.hlist.Tupler
import shapeless.{HNil, Generic, ::, HList}

import scala.reflect.ClassTag

/**
 * A `RequestReader` (a reader monad) that reads a [[Future]] of `A` from the request of type `R`. `RequestReader`s
 * might be composed with each other using either monadic API (`flatMap` method) or applicative API (`::` method).
 * Regardless the API used for `RequestReader`s composition, the main idea behind it is to use primitive readers (i.e.,
 * `param`, `paramOption`) in order to _compose_ them together and _map_ to the application domain data.
 *
 * {{{
 *   case class Complex(r: Double, i: Double)
 *   val complex: RequestReader[Complex] = (
 *     param("real").as[Double] ::
 *     paramOption("imaginary").as[Double].withDefault(0.0)
 *   ).as[Complex]
 * }}}
 *
 */
trait RequestReader[A] { self =>

  /**
   * A [[io.finch.request.items.RequestItem RequestItem]] read by this request reader.
   */
  def item: RequestItem

  /**
   * Reads the data from given request `req`.
   *
   * @param req the request to read
   */
  def apply(req: Request): Future[A]

  /**
   * Flat-maps this request reader to the given function `A => RequestReader[B]`.
   */
  def flatMap[B](fn: A => RequestReader[B]): RequestReader[B] = new RequestReader[B] {
    val item = MultipleItems
    def apply(req: Request): Future[B] = self(req).flatMap(a => fn(a)(req))
  }

  /**
   * Maps this request reader to the given function `A => B`.
   */
  def map[B](fn: A => B): RequestReader[B] = new RequestReader[B] {
    val item = self.item
    def apply(req: Request): Future[B] = self(req).map(fn)
  }

  /**
   * Flat-maps this request reader to the given function `A => Future[B]`.
   */
  def embedFlatMap[B](fn: A => Future[B]): RequestReader[B] = new RequestReader[B] {
    val item = self.item
    def apply(req: Request): Future[B] = self(req).flatMap(fn)
  }

  /**
   * Applies the given filter `p` to this request reader.
   */
  def withFilter(p: A => Boolean): RequestReader[A] = self.should("not fail validation")(p)

  /**
   * Lifts this request reader into one that always succeeds, with an empty option representing failure.
   */
  def lift: RequestReader[Option[A]] = new RequestReader[Option[A]] {
    val item = self.item
    def apply(req: Request): Future[Option[A]] = self(req).liftToTry.map(_.toOption)
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
  def should(rule: String)(predicate: A => Boolean): RequestReader[A] = embedFlatMap(a =>
    if (predicate(a)) Future.value(a)
    else Future.exception(Error.NotValid(self.item, "should " + rule))
  )

  /**
   * Validates the result of this request reader using a `predicate`. The rule is used for error reporting.
   *
   * @param rule text describing the rule being validated
   * @param predicate returns false if the data is valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def shouldNot(rule: String)(predicate: A => Boolean): RequestReader[A] = should(s"not $rule.")(x => !predicate(x))

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
  def should(rule: ValidationRule[A]): RequestReader[A] = should(rule.description)(rule.apply)

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
  def shouldNot(rule: ValidationRule[A]): RequestReader[A] = shouldNot(rule.description)(rule.apply)
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
  def value[A](value: A): RequestReader[A] = const[A](Future.value(value))

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always fails, producing the specified
   * exception.
   *
   * @param exc the exception the new reader should produce
   * @return a new reader that always fails, producing the specified exception
   */
  def exception[A](exc: Throwable): RequestReader[A] = const[A](Future.exception(exc))

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always produces the specified value. It will
   * succeed if the given `Future` succeeds and fail if the `Future` fails.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always produces the specified value
   */
  def const[A](value: Future[A]): RequestReader[A] = embed[A](MultipleItems)(_ => value)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that reads the result from the request.
   *
   * @param f the function to apply to the request
   * @return a new reader that reads the result from the request
   */
  def apply[A](f: Request => A): RequestReader[A] = embed[A](MultipleItems)(req => Future.value(f(req)))

  private[request] def embed[A](i: RequestItem)(f: Request => Future[A]): RequestReader[A] =
    new RequestReader[A] {
      val item = i
      def apply(req: Request): Future[A] = f(req)
    }

  private[this] def notParsed[A](rr: RequestReader[_], tag: ClassTag[_]): PartialFunction[Throwable, Try[A]] = {
    case exc => Throw[A](Error.NotParsed(rr.item, tag, exc))
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[String]` to perform a type conversion based
   * on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when type conversion fails.
   */
  implicit class StringReaderOps(val rr: RequestReader[String]) extends AnyVal {
    def as[A](implicit decoder: DecodeRequest[A], tag: ClassTag[A]): RequestReader[A] =
      rr.embedFlatMap(value => Future.const(decoder(value).rescue(notParsed[A](rr, tag))))
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[Option[String]]` to perform a type conversion
   * based on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails. It will succeed if the
   * result is empty or type conversion succeeds.
   */
  implicit class StringOptionReaderOps(val rr: RequestReader[Option[String]]) extends AnyVal {
    def as[A](implicit decoder: DecodeRequest[A], tag: ClassTag[A]): RequestReader[Option[A]] = rr.embedFlatMap {
      case Some(value) => Future.const(decoder(value).rescue(notParsed[A](rr, tag)) map (Some(_)))
      case None => Future.None
    }

    private[request] def noneIfEmpty: RequestReader[Option[String]] = rr.map {
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
  implicit class StringSeqReaderOps(val rr: RequestReader[Seq[String]]) {

    /* IMPLEMENTATION NOTE: This implicit class should extend AnyVal like all the other ones, to avoid instance creation
     * for each invocation of the extension method. However, this let's us run into a compiler bug when we compile for
     * Scala 2.10: https://issues.scala-lang.org/browse/SI-8018. The bug is caused by the combination of four things:
     * 1) an implicit class, 2) extending AnyVal, 3) wrapping a class with type parameters, 4) a partial function in the
     * body. 2) is the only thing we can easily remove here, otherwise we'd need to move the body of the method
     * somewhere else. Once we drop support for Scala 2.10, this class can safely extends AnyVal.
     */

    def as[A](implicit decoder: DecodeRequest[A], tag: ClassTag[A]): RequestReader[Seq[A]] =
      rr.embedFlatMap { items =>
        val converted = items.map(decoder.apply)
        if (converted.forall(_.isReturn)) Future.value(converted.map(_.get))
        else Future.exception(Error.RequestErrors(converted.collect {
          case Throw(e) => Error.NotParsed(rr.item, tag, e)
        }))
      }
  }

  /**
   * Implicit conversion that adds convenience methods to readers for optional values.
   */
  implicit class OptionReaderOps[A](val rr: RequestReader[Option[A]]) extends AnyVal {
    private[request] def failIfNone: RequestReader[A] = rr.embedFlatMap {
      case Some(value) => Future.value(value)
      case None => Future.exception(Error.NotPresent(rr.item))
    }

    /**
     * If reader is empty it will return provided default value
     */
    def withDefault[B >: A](default: => B): RequestReader[B] = rr.map(_.getOrElse(default))

    /**
     * If reader is empty it will return provided alternative
     */
    def orElse[B >: A](alternative: => Option[B]): RequestReader[Option[B]] = rr.map(_.orElse(alternative))
  }

  /**
   * Implicit class that provides `::` and other operations on any request reader that returns a
   * [[shapeless.HList]].
   *
   * See the implementation note on [[StringSeqReaderOps]] for a discussion of why this is not
   * currently a value class.
   */
  final implicit class HListReaderOps[L <: HList](val self: RequestReader[L]) {

    /**
     * Composes this request reader with the given `that` request reader.
     */
    def ::[S, A](that: RequestReader[A]): RequestReader[A :: L] =
      new RequestReader[A :: L] {
        val item = MultipleItems
        def apply(req: Request): Future[A :: L] =
          Future.join(that(req).liftToTry, self(req).liftToTry).flatMap {
            case (Return(a), Return(l)) => Future.value(a :: l)
            case (Throw(a), Throw(l)) => Future.exception(collectExceptions(a, l))
            case (Throw(e), _) => Future.exception(e)
            case (_, Throw(e)) => Future.exception(e)
          }

        def collectExceptions(a: Throwable, b: Throwable): Error.RequestErrors = {
          def collect(e: Throwable): Seq[Throwable] = e match {
            case Error.RequestErrors(errors) => errors
            case other => Seq(other)
          }

          Error.RequestErrors(collect(a) ++ collect(b))
        }
      }

    /**
     * Converts this request reader to one that returns any type with this [[shapeless.HList]] as
     * its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, L]): RequestReader[A] = self.map(gen.from)

    /**
     * Converts this request reader to one that returns a tuple with the same types as this
     * [[shapeless.HList]].
     *
     * Note that this will fail at compile time if this this [[shapeless.HList]] contains more than
     * 22 elements.
     */
    def asTuple(implicit tupler: Tupler[L]): RequestReader[tupler.Out] = self.map(tupler(_))

    /**
     * Applies a `FunctionN` with the appropriate arity and types and a `Future` return type to
     * the elements of this [[shapeless.HList]].
     */
    def ~~>[F, I, FI](fn: F)(
      implicit ftp: FnToProduct.Aux[F, L => FI], ev: FI <:< Future[I]
      ): RequestReader[I] = self.embedFlatMap(value => ev(ftp(fn)(value)))

    /**
     * Applies a `FunctionN` with the appropriate arity and types to the elements of this
     * [[shapeless.HList]].
     */
    def ~>[F, I](fn: F)(implicit ftp: FnToProduct.Aux[F, L => I]): RequestReader[I] =
      self.map(ftp(fn))
  }

  /**
   * Implicit class that provides `::` and other operations on any request reader to support
   * building [[shapeless.HList]] request readers.
   */
  final implicit class ValueReaderOps[B](val self: RequestReader[B]) extends AnyVal {

    /**
     * Lift this request reader into a singleton [[shapeless.HList]] and compose it with the given
     * `that` request reader.
     */
    def ::[A](that: RequestReader[A]): RequestReader[A :: B :: HNil] =
      that :: self.map(_ :: HNil)

    /**
     * Converts this request reader to one that returns any type with `B :: HNil` as
     * its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, B :: HNil]): RequestReader[A] = self.map { value =>
      gen.from(value :: HNil)
    }

    /**
     * Applies a function returning a future to the result of this reader.
     */
    def ~~>[A](fn: B => Future[A]): RequestReader[A] = self.embedFlatMap(fn)

    /**
     * Applies a function to the result of this reader.
     */
    def ~>[A](fn: B => A): RequestReader[A] = self.map(fn)
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

  def derive[A]: GenericDerivation[A] = new GenericDerivation[A]
}
