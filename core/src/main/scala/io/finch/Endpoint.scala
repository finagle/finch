package io.finch

import cats.{Alternative, Applicative, ApplicativeError, Id, Monad, MonadError}
import cats.data.NonEmptyList
import cats.effect.{Effect, Sync}
import cats.syntax.all._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Service
import com.twitter.finagle.http.{
  Cookie => FinagleCookie,
  Method => FinagleMethod,
  Request,
  Response
}
import com.twitter.finagle.http.exp.{Multipart => FinagleMultipart}
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw, Try}
import io.finch.endpoint._
import io.finch.internal._
import io.finch.items.RequestItem
import scala.reflect.ClassTag
import shapeless._
import shapeless.ops.adjoin.Adjoin
import shapeless.ops.hlist.Tupler

/**
 * An `Endpoint` represents the HTTP endpoint.
 *
 * It is well known and widely adopted in Finagle that "Your Server is a Function"
 * (i.e., `Request => Future[Response]`). In a REST/HTTP API setting this function may be viewed as
 * `Request =1=> (A =2=> Future[B]) =3=> Future[Response]`, where transformation `1` is a request
 * decoding (deserialization), transformation `2` - is a business logic and transformation `3` is -
 * a response encoding (serialization). The only interesting part here is transformation `2` (i.e.,
 * `A => Future[B]`), which represents an application business.
 *
 * An `Endpoint` transformation (`map`, `mapAsync`, etc.) encodes the business logic, while the
 * rest of Finch ecosystem takes care about both serialization and deserialization.
 *
 * A typical way to transform (or map) the `Endpoint` is to use [[internal.Mapper]]:
 *
 * {{{
 *   import io.finch._
 *
 *   case class Foo(i: Int)
 *   case class Bar(s: String)
 *
 *   val foo: Endpoint[Foo] = get("foo") { Ok(Foo(42)) }
 *   val bar: Endpoint[Bar] = get("bar" :: path[String]) { s: String => Ok(Bar(s)) }
 * }}}
 *
 * `Endpoint`s are also composable in terms of or-else combinator (known as a "space invader"
 * operator `:+:`) that takes two `Endpoint`s and returns a coproduct `Endpoint`.
 *
 * {{{
 *   import io.finch._
 *
 *   val foobar: Endpoint[Foo :+: Bar :+: CNil] = foo :+: bar
 * }}}
 *
 * An `Endpoint` might be converted into a Finagle [[Service]] with `Endpoint.toService` method so
 * it can be served within Finagle HTTP.
 *
 * {{{
 *   import com.twitter.finagle.Http
 *
 *   Http.server.serve(foobar.toService)
 * }}}
 */
trait Endpoint[F[_], A] { self =>

  /**
    * Request item (part) that's this endpoint work with.
    */
  def item: items.RequestItem = items.MultipleItems

  /**
    * Runs this endpoint.
    */
  def apply(input: Input): EndpointResult[F, A]

  /**
    * Maps this endpoint to the given function `A => B`.
    */
  final def map[B](fn: A => B)(implicit F: Monad[F]): Endpoint[F, B] =
    mapAsync(fn.andThen(F.pure))

  /**
    * Maps this endpoint to the given function `A => Future[B]`.
    */
  final def mapAsync[B](fn: A => F[B])(implicit F: Monad[F]): Endpoint[F, B] =
    new Endpoint[F, B] with (Output[A] => F[Output[B]]) {

      final def apply(oa: Output[A]): F[Output[B]] = oa.traverse(fn)

      final def apply(input: Input): EndpointResult[F, B] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
          EndpointResult.Matched[F, B](rem, trc, out.flatMap(this))
        case skipped: EndpointResult.NotMatched[F] => skipped
      }

      final override def item = self.item
      final override def toString: String = self.toString
    }

  /**
    * Maps this endpoint to the given function `A => Output[B]`.
    */
  final def mapOutput[B](fn: A => Output[B])(implicit F: Monad[F]): Endpoint[F, B] =
    mapOutputAsync(fn.andThen(F.pure))

  /**
    * Maps this endpoint to the given function `A => Future[Output[B]]`.
    */
  final def mapOutputAsync[B](fn: A => F[Output[B]])(implicit F: Monad[F]): Endpoint[F, B] =
    new Endpoint[F, B] with (Output[A] => F[Output[B]]) {
      final def apply(oa: Output[A]): F[Output[B]] = oa.traverseFlatten(fn)

      final def apply(input: Input): EndpointResult[F, B] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
          EndpointResult.Matched(rem, trc, out.flatMap(this))
        case skipped: EndpointResult.NotMatched[F] => skipped
      }

      override def item = self.item
      final override def toString: String = self.toString
    }

  /**
    * Transforms this endpoint to the given function `Future[Output[A]] => Future[Output[B]]`.
    *
    *
    * Might be useful to perform some extra action on the underlying `Future`. For example, time
    * the latency of the given endpoint.
    *
    * {{{
    *   import io.finch._
    *   import com.twitter.finagle.stats._
    *
    *   def time[A](stat: Stat, e: Endpoint[A]): Endpoint[A] =
    *     e.transform(f => Stat.timeFuture(s)(f))
    * }}}
    */
  final def transform[B](fn: F[Output[A]] => F[Output[B]]): Endpoint[F, B] =
    new Endpoint[F, B] {
      final def apply(input: Input): EndpointResult[F, B] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
          EndpointResult.Matched(rem, trc, fn(out))
        case skipped: EndpointResult.NotMatched[F] => skipped
      }

      override def item = self.item
      final override def toString: String = self.toString
    }

  /**
    * Returns a product of this and `other` endpoint. The resulting endpoint returns a tuple
    * of both values.
    *
    * This combinator is an important piece for Finch's error accumulation. In its current form,
    * `product` will accumulate Finch's own errors (i.e., [[io.finch.Error]]s) into [[io.finch.Errors]]) and
    * will fail-fast with the first non-Finch error (just ordinary `Exception`) observed.
    */
  final def product[B](other: Endpoint[F, B])(implicit F: MonadError[F, Throwable]): Endpoint[F, (A, B)] =
    productWith(other)(Tuple2.apply)

  /**
    * Returns a product of this and `other` endpoint. The resulting endpoint returns a value of
    * resulting type for product function.
    */
  final def productWith[B, O](other: Endpoint[F, B])(p: (A, B) => O)(implicit
    F: MonadError[F, Throwable]
  ): Endpoint[F, O] =
    new Endpoint[F, O] with (((Either[Throwable, Output[A]], Either[Throwable, Output[B]])) => F[Output[O]]) {
      private[this] final def collect(a: Throwable, b: Throwable): Throwable = (a, b) match {
        case (aa: Error, bb: Error) => Errors(NonEmptyList.of(aa, bb))
        case (aa: Error, Errors(bs)) => Errors(aa :: bs)
        case (Errors(as), bb: Error) => Errors(bb :: as)
        case (Errors(as), Errors(bs)) => Errors(as.concatNel(bs))
        case (_: Error, _) => b // we fail-fast with first non-Error observed
        case (_: Errors, _) => b // we fail-fast with first non-Error observed
        case _ => a
      }

      final def apply(both: (Either[Throwable, Output[A]], Either[Throwable, Output[B]])): F[Output[O]] = both match {
        case (Right(oa), Right(ob)) => F.pure(oa.flatMap(a => ob.map(b => p(a, b))))
        case (Left(a), Left(b)) => F.raiseError(collect(a, b))
        case (Left(a), _) => F.raiseError(a)
        case (_, Left(b)) => F.raiseError(b)
      }

      final def apply(input: Input): EndpointResult[F, O] = self(input) match {
        case a @ EndpointResult.Matched(_, _, _) => other(a.rem) match {
          case b @ EndpointResult.Matched(_, _, _) =>
            EndpointResult.Matched(
              b.rem,
              a.trc.concat(b.trc),
              a.out.attempt.product(b.out.attempt).flatMap(this)
            )
          case skipped: EndpointResult.NotMatched[F] => skipped
        }
        case skipped: EndpointResult.NotMatched[F] => skipped
      }

      override def item = self.item
      final override def toString: String = self.toString
    }

  /**
    * Composes this endpoint with the given [[Endpoint]].
    */
  final def ::[B](other: Endpoint[F, B])(implicit
    pa: PairAdjoin[B, A],
    F: MonadError[F, Throwable]
  ): Endpoint[F, pa.Out] = new Endpoint[F, pa.Out] with ((B, A) => pa.Out) {
      private[this] val inner = other.productWith(self)(this)

      final def apply(b: B, a: A): pa.Out = pa(b, a)

      final def apply(input: Input): EndpointResult[F, pa.Out] = inner(input)

      override def item = items.MultipleItems
      final override def toString: String = s"${other.toString} :: ${self.toString}"
    }

  /**
    * Sequentially composes this endpoint with the given `other` endpoint. The resulting endpoint
    * will succeed if either this or `that` endpoints are succeed.
    *
    * == Matching Rules ==
    *
    * - if both endpoints match, the result with a shorter remainder (in terms of consumed route) is picked
    * - if both endpoints don't match, the more specific result (explaining the reason for not matching)
    *   is picked
    */
  final def coproduct[B >: A](other: Endpoint[F, B]): Endpoint[F, B] = new Endpoint[F, B] {
    final def apply(input: Input): EndpointResult[F, B] = self(input) match {
      case a @ EndpointResult.Matched(_, _, _) => other(input) match {
        case b @ EndpointResult.Matched(_, _, _) =>
          if (a.rem.route.length <= b.rem.route.length) a else b
        case _ => a
      }
      case a => other(input) match {
        case EndpointResult.NotMatched.MethodNotAllowed(bb) => a match {
          case EndpointResult.NotMatched.MethodNotAllowed(aa) =>
            EndpointResult.NotMatched.MethodNotAllowed(aa ++ bb)
          case b => b
        }
        case _: EndpointResult.NotMatched[F] => a
        case b => b
      }
    }

    override def item = items.MultipleItems
    final override def toString: String = s"(${self.toString} :+: ${other.toString})"
  }

  /**
    * Composes this endpoint with another in such a way that coproducts are flattened.
    */
  final def :+:[B](that: Endpoint[F, B])(implicit
    a: Adjoin[B :+: A :+: CNil],
    F: MonadError[F, Throwable]
  ): Endpoint[F, a.Out] = {
    val left = that.map(x => a(Inl[B, A :+: CNil](x)))
    val right = self.map(x => a(Inr[B, A :+: CNil](Inl[A, CNil](x))))

    left.coproduct(right)
  }

  /**
    * Converts this endpoint to a Finagle service `Request => Future[Response]` that serves JSON.
    *
    * Consider using [[io.finch.Bootstrap]] instead.
    */
  final def toService(implicit
    tr: ToResponse.Aux[A, Application.Json],
    tre: ToResponse.Aux[Exception, Application.Json],
    F: Effect[F]
  ): Service[Request, Response] = toServiceAs[Application.Json]

  /**
    * Converts this endpoint to a Finagle service `Request => Future[Response]` that serves custom
    * content-type `CT`.
    *
    * Consider using [[io.finch.Bootstrap]] instead.
    */
  final def toServiceAs[CT <: String](implicit
    tr: ToResponse.Aux[A, CT],
    tre: ToResponse.Aux[Exception, CT],
    F: Effect[F]
  ): Service[Request, Response] = Bootstrap.serve[CT](this).toService

  /**
    * Recovers from any exception occurred in this endpoint by creating a new endpoint that will
    * handle any matching throwable from the underlying future.
    */
  final def rescue(pf: PartialFunction[Throwable, F[Output[A]]])(implicit
    F: ApplicativeError[F, Throwable]
  ): Endpoint[F, A] = transform(foa => foa.handleErrorWith(pf))

  /**
    * Recovers from any exception occurred in this endpoint by creating a new endpoint that will
    * handle any matching throwable from the underlying future.
    */
  final def handle(pf: PartialFunction[Throwable, Output[A]])(implicit
    F: ApplicativeError[F, Throwable]
  ): Endpoint[F, A] = rescue(pf.andThen(F.pure))

  /**
    * Validates the result of this endpoint using a `predicate`. The rule is used for error
    * reporting.
    *
    * @param rule text describing the rule being validated
    * @param predicate returns true if the data is valid
    *
    * @return an endpoint that will return the value of this reader if it is valid.
    *         Otherwise the future fails with an [[Error.NotValid]] error.
    */
  final def should(rule: String)(predicate: A => Boolean)(implicit F: MonadError[F, Throwable]): Endpoint[F, A] =
    mapAsync(a =>
      if (predicate(a)) F.pure(a)
      else F.raiseError(Error.NotValid(self.item, "should " + rule))
    )

  /**
    * Validates the result of this endpoint using a `predicate`. The rule is used for error reporting.
    *
    * @param rule text describing the rule being validated
    * @param predicate returns false if the data is valid
    *
    * @return an endpoint that will return the value of this reader if it is valid.
    *         Otherwise the future fails with a [[Error.NotValid]] error.
    */
  final def shouldNot(rule: String)(predicate: A => Boolean)(implicit F: MonadError[F, Throwable]): Endpoint[F, A] =
    should(s"not $rule.")(x => !predicate(x))

  /**
    * Validates the result of this endpoint using a predefined `rule`. This method allows for rules
    * to be reused across multiple endpoints.
    *
    * @param rule the predefined [[ValidationRule]] that will return true if the data is
    *             valid
    *
    * @return an endpoint that will return the value of this reader if it is valid.
    *         Otherwise the future fails with an [[Error.NotValid]] error.
    */
  final def should(rule: ValidationRule[A])(implicit F: MonadError[F, Throwable]): Endpoint[F, A] =
    should(rule.description)(rule.apply)

  /**
    * Validates the result of this endpoint using a predefined `rule`. This method allows for rules
    * to be reused across multiple endpoints.
    *
    * @param rule the predefined [[ValidationRule]] that will return false if the data is
    *             valid
    *
    * @return an endpoint that will return the value of this reader if it is valid.
    *         Otherwise the future fails with a [[Error.NotValid]] error.
    */
  final def shouldNot(rule: ValidationRule[A])(implicit F: MonadError[F, Throwable]): Endpoint[F, A] =
    shouldNot(rule.description)(rule.apply)

  /**
    * Lifts this endpoint into one that always succeeds, with [[Try]] representing both success and
    * failure cases.
    */
  final def liftToTry(implicit F: MonadError[F, Throwable]): Endpoint[F, Try[A]] = attempt.map({
    case Right(r) => Return(r)
    case Left(t) => Throw(t)
  })

  /**
    * Lifts this endpoint into one that always succeeds, with [[Either[Throwable, A]] representing both success and
    * failure cases.
    */
  final def attempt(implicit F: MonadError[F, Throwable]): Endpoint[F, Either[Throwable, A]] =
    new Endpoint[F, Either[Throwable, A]] with (Either[Throwable, Output[A]] => Output[Either[Throwable, A]]) {
      final def apply(toa: Either[Throwable, Output[A]]): Output[Either[Throwable, A]] = toa match {
        case Right(oo) => oo.map(Right.apply)
        case Left(t) => Output.payload(Left(t))
      }

      final def apply(input: Input): EndpointResult[F, Either[Throwable, A]] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
          EndpointResult.Matched(rem, trc, out.attempt.map(this))
        case skipped: EndpointResult.NotMatched[F] => skipped
      }

      override def item = self.item
      override final def toString: String = self.toString
    }

  /**
    * Overrides the `toString` method on this endpoint.
    */
  final def withToString(ts: => String): Endpoint[F, A] = new Endpoint[F, A] {
    final def apply(input: Input): EndpointResult[F, A] = self(input)
    final override def toString: String = ts
  }
}

/**
 * Provides extension methods for [[Endpoint]] to support coproduct and path syntax.
 */
object Endpoint {

  /**
   * Enables a very simple syntax allowing to "map" endpoints to arbitrary functions. The types are
   * resolved at compile time and no reflection is used.
   *
   * For example:
   *
   * {{{
   *   import io.finch._
   *   import io.cats.effect.IO
   *
   *   object Mapping extends Endpoint.Module[IO] {
   *     def hello = get("hello" :: path[String]) { s: String =>
   *       Ok(s)
   *     }
   *   }
   * }}}
   */
  trait Mappable[F[_], A] extends Endpoint[F, A] { self =>
    final def apply(mapper: Mapper[F, A]): Endpoint[F, mapper.Out] = mapper(self)
  }

  /**
   * An alias for [[EndpointResult]].
   */
  type Result[F[_], A] = EndpointResult[F, A]

  /**
   * An alias for [[EndpointModule]].
   */
  type Module[F[_]] = EndpointModule[F]

  final implicit class HListEndpointOps[F[_], L <: HList](val self: Endpoint[F, L]) extends AnyVal {
    /**
     * Converts this endpoint to one that returns any type with this [[shapeless.HList]] as its
     * representation.
     */
    def as[A](implicit gen: Generic.Aux[A, L], F: Monad[F]): Endpoint[F, A] = self.map(gen.from)

    /**
     * Converts this endpoint to one that returns a tuple with the same types as this
     * [[shapeless.HList]].
     *
     * Note that this will fail at compile time if this this [[shapeless.HList]] contains more than
     * 22 elements.
     */
    def asTuple(implicit t: Tupler[L], F: Monad[F]): Endpoint[F, t.Out] = self.map(t(_))
  }

  /**
   * Implicit conversion that adds convenience methods to endpoint for optional values.
   */
  final implicit class OptionEndpointOps[F[_], A](val self: Endpoint[F, Option[A]]) extends AnyVal {
    /**
     * If endpoint is empty it will return provided default value.
     */
    def withDefault[B >: A](default: => B)(implicit F: Monad[F]): Endpoint[F, B] =
      self.map(_.getOrElse(default))

    /**
     * If endpoint is empty it will return provided alternative.
     */
    def orElse[B >: A](alternative: => Option[B])(implicit F: Monad[F]): Endpoint[F, Option[B]] =
      self.map(_.orElse(alternative))
  }

  final implicit class ValueEndpointOps[F[_], B](val self: Endpoint[F, B]) extends AnyVal {
    /**
     * Converts this endpoint to one that returns any type with `B :: HNil` as its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, B :: HNil], F: Monad[F]): Endpoint[F, A] =
      self.map(value => gen.from(value :: HNil))
  }

  implicit def endpointInstances[F[_] : Effect]: Alternative[({type T[B] = Endpoint[F, B]})#T] = {
    new Alternative[({type T[B] = Endpoint[F, B]})#T] {

      final override def ap[A, B](ff: Endpoint[F, A => B])(fa: Endpoint[F, A]): Endpoint[F, B] =
        ff.productWith(fa)((f, a) => f(a))

      final override def product[A, B](fa: Endpoint[F, A], fb: Endpoint[F, B]): Endpoint[F, (A, B)] =
        fa.product(fb)

      final override def combineK[A](x: Endpoint[F, A], y: Endpoint[F, A]): Endpoint[F, A] =
        x.coproduct(y)

      final override def pure[A](x: A): Endpoint[F, A] =
        Endpoint.const[F, A](x)

      final override def empty[A]: Endpoint[F, A] =
        Endpoint.empty[F, A]

      final override def map[A, B](fa: Endpoint[F, A])(f: A => B): Endpoint[F, B] =
        fa.map(f)
    }
  }

  /**
   * Instantiates an [[EndpointModule]] for a given effect type `F`. This is enables better type
   * inference when constucting endpoint instances.
   *
   * For example, `lift` infer the resulting endpoint based on the argument type (string):
   *
   * {{{
   *   import io.finch._, cats.effect.IO
   *   val e = Endpoint[IO].lift("foo") // Endpoint[IO, String]
   * }}}
   */
  def apply[F[_]]: EndpointModule[F] = EndpointModule[F]

  /**
   * Creates an empty [[Endpoint]] (an endpoint that never matches) for a given type.
   */
  def empty[F[_], A]: Endpoint[F, A] =
    new Endpoint[F, A] {
      final def apply(input: Input): Result[F, A] = EndpointResult.NotMatched
    }

  /**
   * An [[Endpoint]] that, when composed with other endpoints, doesn't change anything.
   */
  def zero[F[_]](implicit F: Applicative[F]): Endpoint[F, HNil] =
    new Endpoint[F, HNil] {
      final def apply(input: Input): Result[F, HNil] =
        EndpointResult.Matched(input, Trace.empty, F.pure(Output.payload(HNil)))

      final override def toString: String = ""
    }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given value (evaluated eagerly).
   */
  def const[F[_], A](a: A)(implicit F: Applicative[F]): Endpoint[F, A] =
    new Endpoint[F, A] {
      final def apply(input: Input): Result[F, A] =
        EndpointResult.Matched(input, Trace.empty, F.pure(Output.payload(a)))
    }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given value (evaluated lazily).
   *
   * This might be useful for wrapping functions returning arbitrary value within [[Endpoint]]
   * context.
   *
   * Example: the following endpoint will recompute a random integer on each request.
   *
   * {{{
   *   val nextInt: Endpoint[Int] = Endpoint.lift(scala.util.random.nextInt)
   * }}}
   */
  def lift[F[_], A](a: => A)(implicit F: Sync[F]): Endpoint[F, A] =
    new Endpoint[F, A] {
      final def apply(input: Input): Result[F, A] =
        EndpointResult.Matched(input, Trace.empty, F.delay(Output.payload(a)))
    }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given `F` (evaluated lazily).
   */
  def liftAsync[F[_], A](fa: => F[A])(implicit F: Sync[F]): Endpoint[F, A] =
    new Endpoint[F, A] {
      final def apply(input: Input): Result[F, A] =
        EndpointResult.Matched(input, Trace.empty, F.suspend(fa).map(a => Output.payload(a)))
    }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given `Output` (evaluated lazily).
   */
  def liftOutput[F[_], A](oa: => Output[A])(implicit F: Sync[F]): Endpoint[F, A] =
    new Endpoint[F, A] {
      final def apply(input: Input): Result[F, A] =
        EndpointResult.Matched(input, Trace.empty, F.delay(oa))
    }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given `F[Output]`
   * (evaluated lazily).
   */
  def liftOutputAsync[F[_], A](foa: => F[Output[A]])(implicit F: Sync[F]): Endpoint[F, A] =
    new Endpoint[F, A] {
      final def apply(input: Input): Result[F, A] =
        EndpointResult.Matched(input, Trace.empty, F.suspend(foa))
    }

  /**
   * A root [[Endpoint]] that always matches and extracts the current request.
   */
  def root[F[_]](implicit F: Effect[F]): Endpoint[F, Request] =
    new Endpoint[F, Request] {
      final def apply(input: Input): Result[F, Request] =
        EndpointResult.Matched(input, Trace.empty, F.delay(Output.payload(input.request)))

      final override def toString: String = "root"
    }

  /**
   * An [[Endpoint]] that always matches any path.
   */
  def pathAny[F[_]](implicit F: Applicative[F]): Endpoint[F, HNil] =
    new Endpoint[F, HNil] {
      final def apply(input: Input): Result[F, HNil] =
        EndpointResult.Matched(input.withRoute(Nil), Trace.empty, F.pure(Output.payload(HNil)))

      final override def toString: String = "*"
    }

  /**
   * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
   * [[DecodePath]] instances defined for `A`) from the current path segment.
   */
  def path[F[_]: Effect, A: DecodePath: ClassTag]: Endpoint[F, A] =
    new ExtractPath[F, A]

  /**
   * A matching [[Endpoint]] that reads a tail value `A` (using the implicit
   * [[DecodePath]] instances defined for `A`) from the entire path.
   */
  def paths[F[_]: Effect, A: DecodePath: ClassTag]: Endpoint[F, Seq[A]] =
    new ExtractPaths[F, A]

  /**
   * An [[Endpoint]] that matches a given string.
   */
  def path[F[_]: Effect](s: String): Endpoint[F, HNil] =
    new MatchPath[F](s)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `GET` and the underlying
   * endpoint succeeds on it.
   */
  def get[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Get, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `POST` and the underlying
   * endpoint succeeds on it.
   */
  def post[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Post, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PATCH` and the underlying
   * endpoint succeeds on it.
   */
  def patch[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Patch, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `DELETE` and the
   * underlying endpoint succeeds on it.
   */
  def delete[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Delete, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `HEAD` and the underlying
   * endpoint succeeds on it.
   */
  def head[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Head, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `OPTIONS` and the
   * underlying endpoint succeeds on it.
   */
  def options[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Options, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PUT` and the underlying
   * endpoint succeeds on it.
   */
  def put[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Put, e)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `TRACE` and the underlying
   * router endpoint on it.
   */
  def trace[F[_], A](e: Endpoint[F, A]): Mappable[F, A] =
    new Method[F, A](FinagleMethod.Trace, e)

  /**
   * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
   * an [[Error.NotPresent]] exception when the header is missing.
   */
  def header[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, A] =
    new Header[F, Id, A](name) with Header.Required[F, A]

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, Option[A]] =
    new Header[F, Option, A](name) with Header.Optional[F, A]

  /**
   * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
   * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  def binaryBodyOption[F[_]: Effect]: Endpoint[F, Option[Array[Byte]]] =
    new BinaryBody[F, Option[Array[Byte]]] with FullBody.Optional[F, Array[Byte]]

  /**
   * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
   * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
   * matches non-chunked (non-streamed) requests.
   */
  def binaryBody[F[_]: Effect]: Endpoint[F, Array[Byte]] =
    new BinaryBody[F, Array[Byte]] with FullBody.Required[F, Array[Byte]]

  /**
   * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
   * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  def stringBodyOption[F[_]: Effect]: Endpoint[F, Option[String]] =
    new StringBody[F, Option[String]] with FullBody.Optional[F, String]

  /**
   * An evaluating [[Endpoint]] that reads the required request body, interpreted as a `String`, or
   * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  def stringBody[F[_]: Effect]: Endpoint[F, String] =
    new StringBody[F, String] with FullBody.Required[F, String]

  /**
   * An [[Endpoint]] that reads an optional request body represented as `CT` (`ContentType`) and
   * interpreted as `A`, into an `Option`. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  def bodyOption[F[_]: Effect, A: ClassTag, CT](implicit D: Decode.Dispatchable[A, CT]): Endpoint[F, Option[A]] =
    new Body[F, A, Option[A], CT] with FullBody.Optional[F, A]

  /**
   * An [[Endpoint]] that reads the required request body represented as `CT` (`ContentType`) and
   * interpreted as `A`, or throws an [[Error.NotPresent]] exception. The returned [[Endpoint]]
   * only matches non-chunked (non-streamed) requests.
   */
  def body[F[_]: Effect, A: ClassTag, CT](implicit d: Decode.Dispatchable[A, CT]): Endpoint[F, A] =
    new Body[F, A, A, CT] with FullBody.Required[F, A]

  /**
   * Alias for `body[F, A, Application.Json]`.
   */
  def jsonBody[F[_]: Effect, A: Decode.Json: ClassTag]: Endpoint[F, A] =
    body[F, A, Application.Json]

  /**
   * Alias for `bodyOption[F, A, Application.Json]`.
   */
  def jsonBodyOption[F[_]: Effect, A: Decode.Json: ClassTag]: Endpoint[F, Option[A]] =
    bodyOption[F, A, Application.Json]

  /**
   * Alias for `body[F, A, Text.Plain]`
   */
  def textBody[F[_]: Effect, A: Decode.Text: ClassTag]: Endpoint[F, A] =
    body[F, A, Text.Plain]

  /**
   * Alias for `bodyOption[A, Text.Plain]`
   */
  def textBodyOption[F[_]: Effect, A: Decode.Text: ClassTag]: Endpoint[F, Option[A]] =
    bodyOption[F, A, Text.Plain]

  /**
   * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
   * an `AsyncStream[Buf]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
   */
  def asyncBody[F[_]](implicit F: Effect[F]): Endpoint[F, AsyncStream[Buf]] =
    new Endpoint[F, AsyncStream[Buf]] {
      final def apply(input: Input): EndpointResult[F, AsyncStream[Buf]] =
        if (!input.request.isChunked) EndpointResult.NotMatched
        else
          EndpointResult.Matched(
            input,
            Trace.empty,
            F.delay(Output.payload(AsyncStream.fromReader(input.request.reader)))
          )

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "asyncBody"
    }

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP cookie from the request into an
   * `Option`.
   */
  def cookieOption[F[_]: Effect](name: String): Endpoint[F, Option[FinagleCookie]] =
    new Cookie[F, Option[FinagleCookie]](name) with Cookie.Optional[F]

  /**
   * An evaluating [[Endpoint]] that reads a required cookie from the request or raises an
   * [[Error.NotPresent]] exception when the cookie is missing.
   */
  def cookie[F[_]: Effect](name: String): Endpoint[F, FinagleCookie] =
    new Cookie[F, FinagleCookie](name) with Cookie.Required[F]

  /**
   * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
   * into an `Option`.
   */
  def paramOption[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, Option[A]] =
    new Param[F, Option, A](name) with Param.Optional[F, A]

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, A] =
    new Param[F, Id, A](name) with Param.Required[F, A]

  /**
   * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
   * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
   */
  def params[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, Seq[A]] =
    new Params[F, Seq, A](name) with Params.AllowEmpty[F, A]

  /**
   * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
   * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
   * when the params are missing or empty.
   */
  def paramsNel[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, NonEmptyList[A]] =
    new Params[F, NonEmptyList, A](name) with Params.NonEmpty[F, A]

  /**
   * An evaluating [[Endpoint]] that reads an optional file upload from a `multipart/form-data`
   * request into an `Option`.
   */
  def multipartFileUploadOption[F[_]: Effect](name: String): Endpoint[F, Option[FinagleMultipart.FileUpload]] =
    new FileUpload[F, Option](name) with FileUpload.Optional[F]

  /**
   * An evaluating [[Endpoint]] that reads a required file upload from a `multipart/form-data`
   * request.
   */
  def multipartFileUpload[F[_]: Effect](name: String): Endpoint[F, FinagleMultipart.FileUpload] =
    new FileUpload[F, Id](name) with FileUpload.Required[F]

  /**
   * An evaluating [[Endpoint]] that optionally reads multiple file uploads from a
   * `multipart/form-data` request.
   */
  def multipartFileUploads[F[_]: Effect](name: String): Endpoint[F, Seq[FinagleMultipart.FileUpload]] =
    new FileUpload[F, Seq](name) with FileUpload.AllowEmpty[F]

  /**
   * An evaluating [[Endpoint]] that requires multiple file uploads from a `multipart/form-data`
   * request.
   */
  def multipartFileUploadsNel[F[_]: Effect](name: String): Endpoint[F, NonEmptyList[FinagleMultipart.FileUpload]] =
    new FileUpload[F, NonEmptyList](name) with FileUpload.NonEmpty[F]

  /**
   * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttribute[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, A] =
    new Attribute[F, Id, A](name) with Attribute.Required[F, A] with Attribute.SingleError[F, Id, A]

  /**
   * An evaluating [[Endpoint]] that reads an optional attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttributeOption[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, Option[A]] =
    new Attribute[F, Option, A](name) with Attribute.Optional[F, A] with Attribute.SingleError[F, Option, A]

  /**
   * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttributes[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, Seq[A]] =
    new Attribute[F, Seq, A](name) with Attribute.AllowEmpty[F, A] with Attribute.MultipleErrors[F, Seq, A]

  /**
   * An evaluating [[Endpoint]] that reads a required attribute from a `multipart/form-data`
   * request.
   */
  def multipartAttributesNel[F[_]: Effect, A: DecodeEntity: ClassTag](name: String): Endpoint[F, NonEmptyList[A]] =
    new Attribute[F, NonEmptyList, A](name) with Attribute.NonEmpty[F, A] with Attribute.MultipleErrors[F, NonEmptyList, A]
}
