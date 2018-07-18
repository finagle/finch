package io.finch

import arrows.twitter.Task
import cats.Alternative
import cats.data.NonEmptyList
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Return, Throw, Try}
import io.finch.internal._
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
 * A typical way to transform (or map) the `Endpoint` is to use [[io.finch.syntax.Mapper]]:
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
trait Endpoint[A] { self =>

  /**
   * Request item (part) that's this endpoint work with.
   */
  def item: items.RequestItem = items.MultipleItems

  /**
   * Runs this endpoint.
   */
  def apply(input: Input): Endpoint.Result[A]

  /**
   * Maps this endpoint to the given function `A => B`.
   */
  final def map[B](fn: A => B): Endpoint[B] =
    mapAsync(fn.andThen(Future.value))


  /**
   * Maps this endpoint to the given function `A => Future[B]`.
    * B == Output[A]
   */
  final def mapAsync[B](fn: A => Future[B]): Endpoint[B] =
    new Endpoint[B] with (Output[A] => Task[Output[B]]) {

      final def apply(oa: Output[A]): Task[Output[B]] = Task.async(oa.traverse(fn))

      final def apply(input: Input): Endpoint.Result[B] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
              EndpointResult.Matched(rem, trc, out.flatMap(this))
        case skipped: EndpointResult.NotMatched => skipped
      }

      final override def item = self.item
      final override def toString: String = self.toString
  }

  /**
   * Maps this endpoint to the given function `A => Output[B]`.
   */
  final def mapOutput[B](fn: A => Output[B]): Endpoint[B] =
    mapOutputAsync(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Future[Output[B]]`.
   */
  final def mapOutputAsync[B](fn: A => Future[Output[B]]): Endpoint[B] =
    new Endpoint[B] with (Output[A] => Task[Output[B]]) {
      final def apply(oa: Output[A]): Task[Output[B]] = Task.async(oa.traverseFlatten(fn))

      final def apply(input: Input): Endpoint.Result[B] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
            EndpointResult.Matched(rem, trc, out.flatMap(this))
        case skipped: EndpointResult.NotMatched => skipped
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
  final def transform[B](fn: Future[Output[A]] => Future[Output[B]]): Endpoint[B] =
    new Endpoint[B] {
      final def apply(input: Input): Endpoint.Result[B] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
            EndpointResult.Matched(rem, trc, out.flatMap(a => Task.async(fn(Future.value(a)))))
        case skipped: EndpointResult.NotMatched => skipped
      }

      override def item = self.item
      final override def toString: String = self.toString
   }

  /**
   * Returns a product of this and `other` endpoint. The resulting endpoint returns a tuple
   * of both values.
   *
   * This combinator is an important piece for Finch's error accumulation. In its current form,
   * `product` will accumulate Finch's own errors (i.e., [[Error]]s) into [[Errors]]) and
   * will fail-fast with the first non-Finch error (just ordinary `Exception`) observed.
   */
  final def product[B](other: Endpoint[B]): Endpoint[(A, B)] = productWith(other)(Tuple2.apply)

  /**
   * Returns a product of this and `other` endpoint. The resulting endpoint returns a value of
   * resulting type for product function.
   */
  final def productWith[B, O](other: Endpoint[B])(p: (A, B) => O): Endpoint[O] =
    new Endpoint[O] with (((Try[Output[A]], Try[Output[B]])) => Task[Output[O]]) {
      private[this] final def collect(a: Throwable, b: Throwable): Throwable = (a, b) match {
        case (aa: Error, bb: Error) => Errors(NonEmptyList.of(aa, bb))
        case (aa: Error, Errors(bs)) => Errors(aa :: bs)
        case (Errors(as), bb: Error) => Errors(bb :: as)
        case (Errors(as), Errors(bs)) => Errors(as.concatNel(bs))
        case (_: Error, _) => b // we fail-fast with first non-Error observed
        case (_: Errors, _) => b // we fail-fast with first non-Error observed
        case _ => a
      }

      final def apply(both: (Try[Output[A]], Try[Output[B]])): Task[Output[O]] = both match {
        case (Return(oa), Return(ob)) => Task.value(oa.flatMap(a => ob.map(b => p(a, b))))
        case (Throw(a), Throw(b)) => Task.exception(collect(a, b))
        case (Throw(a), _) => Task.exception(a)
        case (_, Throw(b)) => Task.exception(b)
      }

      final def apply(input: Input): Endpoint.Result[O] = self(input) match {
        case a @ EndpointResult.Matched(_, _, _) => other(a.rem) match {
          case b @ EndpointResult.Matched(_, _, _) =>
            {
              EndpointResult.Matched(
                b.rem,
                a.trc.concat(b.trc),
                a.out.liftToTry.join(b.out.liftToTry).flatMap(this)
              )
            }
          case skipped: EndpointResult.NotMatched => skipped
        }
        case skipped: EndpointResult.NotMatched => skipped
      }

      override def item = self.item
      final override def toString: String = self.toString
    }

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  final def ::[B](other: Endpoint[B])(implicit pa: PairAdjoin[B, A]): Endpoint[pa.Out] =
    new Endpoint[pa.Out] with ((B, A) => pa.Out) {
      private[this] val inner = other.productWith(self)(this)

      final def apply(b: B, a: A): pa.Out = pa(b, a)

      final def apply(input: Input): Endpoint.Result[pa.Out] = inner(input)

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
  final def coproduct[B >: A](other: Endpoint[B]): Endpoint[B] = new Endpoint[B] {
    final def apply(input: Input): Endpoint.Result[B] = self(input) match {
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
        case EndpointResult.NotMatched => a
        case b => b
      }
    }

    override def item = items.MultipleItems
    final override def toString: String = s"(${self.toString} :+: ${other.toString})"
  }

  /**
   * Composes this endpoint with another in such a way that coproducts are flattened.
   */
  final def :+:[B](that: Endpoint[B])(implicit a: Adjoin[B :+: A :+: CNil]): Endpoint[a.Out] = {
    val left = that.map(x => a(Inl[B, A :+: CNil](x)))
    val right = self.map(x => a(Inr[B, A :+: CNil](Inl[A, CNil](x))))

    left.coproduct(right)
  }

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]` that serves JSON.
   *
   * Consider using [[Bootstrap]] instead.
   */
  final def toService(implicit
    tr: ToResponse.Aux[A, Application.Json],
    tre: ToResponse.Aux[Exception, Application.Json]
  ): Service[Request, Response] = toServiceAs[Application.Json]

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]` that serves custom
   * content-type `CT`.
   *
   * Consider using [[Bootstrap]] instead.
   */
  final def toServiceAs[CT <: String](implicit
    tr: ToResponse.Aux[A, CT],
    tre: ToResponse.Aux[Exception, CT]
  ): Service[Request, Response] = Bootstrap.serve[CT](this).toService

  /**
   * Recovers from any exception occurred in this endpoint by creating a new endpoint that will
   * handle any matching throwable from the underlying future.
   */
  final def rescue[B >: A](pf: PartialFunction[Throwable, Future[Output[B]]]): Endpoint[B] =
    transform(foa => foa.rescue(pf))

  /**
   * Recovers from any exception occurred in this endpoint by creating a new endpoint that will
   * handle any matching throwable from the underlying future.
   */
  final def handle[B >: A](pf: PartialFunction[Throwable, Output[B]]): Endpoint[B] =
    rescue(pf.andThen(Future.value))

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
  final def should(rule: String)(predicate: A => Boolean): Endpoint[A] = mapAsync(a =>
    if (predicate(a)) Future.value(a)
    else Future.exception(Error.NotValid(self.item, "should " + rule))
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
  final def shouldNot(rule: String)(predicate: A => Boolean): Endpoint[A] =
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
  final def should(rule: ValidationRule[A]): Endpoint[A] = should(rule.description)(rule.apply)

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
  final def shouldNot(rule: ValidationRule[A]): Endpoint[A] = shouldNot(rule.description)(rule.apply)

  /**
    * Lifts this endpoint into one that always succeeds, with [[Try]] representing both success and
    * failure cases.
    */
  final def liftToTry: Endpoint[Try[A]] =
    new Endpoint[Try[A]] with (Try[Output[A]] => Output[Try[A]]) {
      final def apply(toa: Try[Output[A]]): Output[Try[A]] = toa match {
        case Return(oo) => oo.map(Return.apply)
        case t @ Throw(_) => Output.payload(t.cast[A])
      }

      final def apply(input: Input): Endpoint.Result[Try[A]] = self(input) match {
        case EndpointResult.Matched(rem, trc, out) =>
          EndpointResult.Matched(rem, trc, out.liftToTry.map(this))
        case skipped: EndpointResult.NotMatched => skipped
      }

      override def item = self.item
      override final def toString: String = self.toString
    }

  /**
   * Overrides the `toString` method on this endpoint.
   */
  final def withToString(ts: => String): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Endpoint.Result[A] = self(input)
    final override def toString: String = ts
  }
}

/**
 * Provides extension methods for [[Endpoint]] to support coproduct and path syntax.
 */
object Endpoint {

  type Result[A] = EndpointResult[A]

  /**
   * Creates an empty [[Endpoint]] (an endpoint that never matches) for a given type.
   */
  def empty[A]: Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Result[A] = EndpointResult.NotMatched
  }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given value (evaluated eagerly).
   */
  def const[A](a: A): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Result[A] =
      EndpointResult.Matched(input, Trace.empty, Task.value(Output.payload(a)))
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
  def lift[A](a: => A): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Result[A] =
      EndpointResult.Matched(input, Trace.empty, Task(Output.payload(a)))
  }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given `Future` (evaluated lazily).
   */
  def liftAsync[A](fa: => Future[A]): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Result[A] =
        EndpointResult.Matched(input, Trace.empty, Task.async(fa.map(a => Output.payload(a))))
  }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given `Output` (evaluated lazily).
   */
  def liftOutput[A](oa: => Output[A]): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Result[A] =
      EndpointResult.Matched(input, Trace.empty, Task(oa))
  }

  /**
   * Creates an [[Endpoint]] that always matches and returns a given `Future[Output]`
   * (evaluated lazily).
   */
  def liftOutputAsync[A](foa: => Future[Output[A]]): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Result[A] =
      EndpointResult.Matched(input, Trace.empty, Task.async(foa))
  }

  final implicit class ValueEndpointOps[B](val self: Endpoint[B]) extends AnyVal {
    /**
     * Converts this endpoint to one that returns any type with `B :: HNil` as its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, B :: HNil]): Endpoint[A] =
      self.map(value => gen.from(value :: HNil))
  }

  final implicit class HListEndpointOps[L <: HList](val self: Endpoint[L]) extends AnyVal {
    /**
     * Converts this endpoint to one that returns any type with this [[shapeless.HList]] as its
     * representation.
     */
    def as[A](implicit gen: Generic.Aux[A, L]): Endpoint[A] = self.map(gen.from)

    /**
     * Converts this endpoint to one that returns a tuple with the same types as this
     * [[shapeless.HList]].
     *
     * Note that this will fail at compile time if this this [[shapeless.HList]] contains more than
     * 22 elements.
     */
    def asTuple(implicit t: Tupler[L]): Endpoint[t.Out] = self.map(t(_))
  }

  /**
   * Implicit conversion that adds convenience methods to endpoint for optional values.
   */
  implicit class OptionEndpointOps[A](val self: Endpoint[Option[A]]) extends AnyVal {
    /**
     * If endpoint is empty it will return provided default value.
     */
    def withDefault[B >: A](default: => B): Endpoint[B] = self.map(_.getOrElse(default))

    /**
     * If endpoint is empty it will return provided alternative.
     */
    def orElse[B >: A](alternative: => Option[B]): Endpoint[Option[B]] =
      self.map(_.orElse(alternative))
  }

  implicit val endpointInstance: Alternative[Endpoint] = new Alternative[Endpoint] {
    final override def ap[A, B](ff: Endpoint[A => B])(fa: Endpoint[A]): Endpoint[B] =
      ff.productWith(fa)((f, a) => f(a))

    final override def map[A, B](fa: Endpoint[A])(f: A => B): Endpoint[B] =
      fa.map(f)

    final override def product[A, B](fa: Endpoint[A], fb: Endpoint[B]): Endpoint[(A, B)] =
      fa.product(fb)

    final override def pure[A](x: A): Endpoint[A] =
      Endpoint.const(x)

    final override def empty[A]: Endpoint[A] =
      Endpoint.empty[A]

    final override def combineK[A](x: Endpoint[A], y: Endpoint[A]): Endpoint[A] =
      x.coproduct(y)
  }
}
