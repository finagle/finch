package io.finch

import cats.Alternative
import cats.data.NonEmptyList
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Cookie, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.{Future, Return, Throw, Try}
import io.catbird.util.Rerunnable
import io.finch.internal._
import java.nio.charset.Charset
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
 * A typical way to transform (or map) the `Endpoint` is to use [[Mapper]] and `Endpoint.apply`
 * method, which, depending on the argument type, delegates the map operation to the underlying
 * function.
 *
 * {{{
 *   import io.finch._
 *
 *   case class Foo(i: Int)
 *   case class Bar(s: String)
 *
 *   val foo: Endpoint[Foo] = get("foo") { Ok(Foo(42)) }
 *   val bar: Endpoint[Bar] = get("bar" :: string) { s: String => Ok(Bar(s)) }
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
   * Maps this endpoint to either `A => Output[B]` or `A => Output[Future[B]]`.
   */
  final def apply(mapper: Mapper[A]): Endpoint[mapper.Out] = mapper(self)

  // There is a reason why `apply` can't be renamed to `run` as per
  //   https://github.com/finagle/finch/issues/371.
  // More details are here:
  //   http://stackoverflow.com/questions/32064375/magnet-pattern-and-overloaded-methods

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
   */
  final def mapAsync[B](fn: A => Future[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Endpoint.Result[B] =
      self(input).map {
        case (remainder, output) => remainder -> output.flatMapF(oa => oa.traverse(a => fn(a)))
      }

    override def item = self.item
    override def toString = self.toString
  }

  /**
   * Maps this endpoint to the given function `A => Output[B]`.
   */
  final def mapOutput[B](fn: A => Output[B]): Endpoint[B] =
    mapOutputAsync(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Future[Output[B]]`.
   */
  final def mapOutputAsync[B](fn: A => Future[Output[B]]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Endpoint.Result[B] =
      self(input).map {
        case (remainder, output) => remainder -> output.flatMapF { oa =>
            val fob = oa.traverse(fn).map(oob => oob.flatten)

            fob.map { ob =>
              val ob1 = oa.headers.foldLeft(ob)((acc, x) => acc.withHeader(x))
              val ob2 = oa.cookies.foldLeft(ob1)((acc, x) => acc.withCookie(x))

              ob2
            }
          }
      }

    override def item = self.item
    override def toString = self.toString
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
  final def transform[B](fn: Future[Output[A]] => Future[Output[B]]): Endpoint[B] = new Endpoint[B] {
    override def apply(input: Input): Endpoint.Result[B] = {
      self(input).map {
        case (remainder, output) => remainder -> new Rerunnable[Output[B]] {
          override def run: Future[Output[B]] = fn(output.run)
        }
      }
    }
  }

  final def product[B](other: Endpoint[B]): Endpoint[(A, B)] = new Endpoint[(A, B)] {
    private[this] def join(both: (Try[Output[A]], Try[Output[B]])): Future[Output[(A, B)]] =
      both match {
        case (Return(oa), Return(ob)) => Future.value(oa.flatMap(a => ob.map(b => (a, b))))
        case (Throw(oa), Throw(ob)) => Future.exception(collectExceptions(oa, ob))
        case (Throw(e), _) => Future.exception(e)
        case (_, Throw(e)) => Future.exception(e)
      }

    private[this] def collectExceptions(a: Throwable, b: Throwable): Error.RequestErrors = {
      def collect(e: Throwable): Seq[Throwable] = e match {
        case Error.RequestErrors(errors) => errors
        case rest => Seq(rest)
      }

      Error.RequestErrors(collect(a) ++ collect(b))
    }

    def apply(input: Input): Endpoint.Result[(A, B)] =
      self(input).flatMap {
        case (remainder1, outputA) => other(remainder1).map {
          case (remainder2, outputB) =>
            remainder2 -> outputA.liftToTry.product(outputB.liftToTry).flatMapF(join)
        }
      }

    override def item = self.item
    override def toString = self.toString
  }

  /**
   * Maps this endpoint to `Endpoint[A => B]`.
   */
  @deprecated("Use product or Applicative[Endpoint].ap", "0.11.0")
  final def ap[B](fn: Endpoint[A => B]): Endpoint[B] = product(fn).map {
    case (a, f) => f(a)
  }

  /**
   * Composes this endpoint with the given `other` endpoint. The resulting endpoint will succeed
   * only if both this and `that` endpoints succeed.
   */
  final def adjoin[B](other: Endpoint[B])(implicit
    pairAdjoin: PairAdjoin[A, B]
  ): Endpoint[pairAdjoin.Out] = new Endpoint[pairAdjoin.Out] {
    val inner = self.product(other).map {
      case (a, b) => pairAdjoin(a, b)
    }
    def apply(input: Input): Endpoint.Result[pairAdjoin.Out] = inner(input)

    override def item = items.MultipleItems
    override def toString = s"${self.toString}/${other.toString}"
  }

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  @deprecated("Use :: instead", "0.11")
  final def ?[B](other: Endpoint[B])(implicit adjoin: PairAdjoin[A, B]): Endpoint[adjoin.Out] =
    self.adjoin(other)

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  @deprecated("Use :: instead", "0.11")
  final def /[B](other: Endpoint[B])(implicit adjoin: PairAdjoin[A, B]): Endpoint[adjoin.Out] =
    self.adjoin(other)

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  final def ::[B](other: Endpoint[B])(implicit adjoin: PairAdjoin[B, A]): Endpoint[adjoin.Out] =
    other.adjoin(self)

  /**
   * Sequentially composes this endpoint with the given `other` endpoint. The resulting endpoint
   * will succeed if either this or `that` endpoints are succeed.
   */
  final def |[B >: A](other: Endpoint[B]): Endpoint[B] = new Endpoint[B] {
    private[this] def aToB(o: Endpoint.Result[A]): Endpoint.Result[B] =
      o.asInstanceOf[Endpoint.Result[B]]

    def apply(input: Input): Endpoint.Result[B] =
      (self(input), other(input)) match {
        case (aa @ Some((a, _)), bb @ Some((b, _))) =>
          if (a.path.length <= b.path.length) aToB(aa) else bb
        case (a, b) => aToB(a).orElse(b)
      }

    override def item = items.MultipleItems
    override def toString = s"(${self.toString}|${other.toString})"
  }

  /**
   * Composes this endpoint with another in such a way that coproducts are flattened.
   */
  final def :+:[B](that: Endpoint[B])(implicit a: Adjoin[B :+: A :+: CNil]): Endpoint[a.Out] =
    that.map(x => a(Inl[B, A :+: CNil](x))) |
    self.map(x => a(Inr[B, A :+: CNil](Inl[A, CNil](x))))

  final def withHeader(header: (String, String)): Endpoint[A] =
    withOutput(o => o.withHeader(header))

  final def withCookie(cookie: Cookie): Endpoint[A] =
    withOutput(o => o.withCookie(cookie))

  final def withCharset(charset: Charset): Endpoint[A] =
    withOutput(o => o.withCharset(charset))

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]` that serves JSON.
   */
  @deprecated("Use toServiceAs[Application.Json] instead", "0.11")
  final def toService(implicit
    tr: ToResponse.Aux[A, Application.Json],
    tre: ToResponse.Aux[Exception, Application.Json]
  ): Service[Request, Response] = toServiceAs[Application.Json]

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]` that serves custom
   * content-type `CT`.
   */
  final def toServiceAs[CT <: String](implicit
    tr: ToResponse.Aux[A, CT],
    tre: ToResponse.Aux[Exception, CT]
  ): Service[Request, Response] = new Service[Request, Response] {

    private[this] val basicEndpointHandler: PartialFunction[Throwable, Output[Nothing]] = {
      case e: io.finch.Error => Output.failure(e, Status.BadRequest)
    }

    private[this] val safeEndpoint = self.handle(basicEndpointHandler)

    def apply(req: Request): Future[Response] = safeEndpoint(Input.request(req)) match {
      case Some((remainder, output)) if remainder.isEmpty =>
        output.map(oa => oa.toResponse[CT](req.version)).run
      case _ => Future.value(Response(req.version, Status.NotFound))
    }
  }

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
   * Lifts this endpoint into one that always succeeds, with an empty `Option` representing failure.
   */
  final def lift: Endpoint[Option[A]] = new Endpoint[Option[A]] {
    def apply(input: Input): Endpoint.Result[Option[A]] =
      self(input).map {
        case (remainder, output) =>
          remainder -> output.liftToTry
            .map(toa => toa.toOption.fold(Output.None: Output[Option[A]])(o => o.map(Some.apply)))
      }

    override def item = self.item
    override def toString = self.toString
  }

  private[this] def withOutput[B](fn: Output[A] => Output[B]): Endpoint[B] =
    transform(foa => foa.map(oa => fn(oa)))
}

/**
 * Provides extension methods for [[Endpoint]] to support coproduct and path syntax.
 */
object Endpoint {

  type Result[A] = Option[(Input, Rerunnable[Output[A]])]

  /**
   * Creates an [[Endpoint]] from the given [[Output]].
   */
  def apply(mapper: Mapper[shapeless.HNil]): Endpoint[mapper.Out] = mapper(/)

  /**
   * Creates an empty [[Endpoint]] (an endpoint that never matches) for a given type.
   */
  def empty[A]: Endpoint[A] = new Endpoint[A] {
    def apply(input: Input): Result[A] = None
  }

  private[finch] def embed[A](i: items.RequestItem)(f: Input => Result[A]): Endpoint[A] =
    new Endpoint[A] {
      def apply(input: Input): Result[A] = f(input)

      override def item: items.RequestItem = i
      override def toString: String =
        s"${item.kind}${item.nameOption.map(n => "(" + n + ")").getOrElse("")}"
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

  private[this] def notParsed[A](
    e: Endpoint[_], tag: ClassTag[_]
  ): PartialFunction[Throwable, Try[A]] = {
    case exc => Throw[A](Error.NotParsed(e.item, tag, exc))
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `Endpoint[String]` to perform a type
   * conversion based on an implicit `DecodeEntity[A]` which must be in scope.
   *
   * The resulting endpoint will fail when type conversion fails.
   */
  implicit class StringEndpointOps(val self: Endpoint[String]) extends AnyVal {
    def as[A](implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
      self.mapAsync(value => Future.const(d(value).rescue(notParsed[A](self, tag))))
  }

  implicit class BufEndpointOps(self: Endpoint[Buf]) {
    def as[A](implicit d: Decode.Json[A]): Endpoint[A] = new Endpoint[A] {
      // TODO: Will be better with StateT
      // See https://github.com/finagle/finch/pull/559
      def apply(input: Input): Endpoint.Result[A] =
        self.mapAsync(value => Future.const(d(value, input.request.charsetOrUtf8))).apply(input)
    }
  }

  /**
   * Implicit conversion that adds convenience methods to endpoint for optional values.
   */
  implicit class OptionEndpointOps[A](val self: Endpoint[Option[A]]) extends AnyVal {
    private[finch] def failIfNone: Endpoint[A] = self.mapAsync {
      case Some(value) => Future.value(value)
      case None => Future.exception(Error.NotPresent(self.item))
    }

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

  /**
   * Implicit conversion that allows to call `as[A]` on any `Endpoint[NonEmptyList[String]]` to perform a
   * type conversion based on an implicit `Decode[A]` which must be in scope.
   *
   * The resulting endpoint will fail when type conversion fails on one
   * or more of the elements in the `NonEmptyList`. It will succeed if type conversion succeeds for all elements.
   */
  implicit class StringNelEndpointOps(val self: Endpoint[NonEmptyList[String]]) {

    /* IMPLEMENTATION NOTE: This implicit class should extend AnyVal like all the other ones, to
     * avoid instance creation for each invocation of the extension method. However, this let's us
     * run into a compiler bug when we compile for Scala 2.10:
     * https://issues.scala-lang.org/browse/SI-8018. The bug is caused by the combination of four
     * things: 1) an implicit class, 2) extending AnyVal, 3) wrapping a class with type parameters,
     * 4) a partial function in the body. 2) is the only thing we can easily remove here, otherwise
     * we'd need to move the body of the method somewhere else. Once we drop support for Scala 2.10,
     * this class can safely extends AnyVal.
     */

    def as[A](implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[NonEmptyList[A]] =
      self.mapAsync { items =>
        val decoded = items.toList.map(d.apply)
        val errors = decoded.collect {
          case Throw(e) => Error.NotParsed(self.item, tag, e)
        }

        if (errors.isEmpty)
          Future.const(Try.collect(decoded).map(seq => NonEmptyList(seq.head, seq.tail.toList)))
        else
          Future.exception(Error.RequestErrors(errors))
      }
  }

  /**
    * Implicit conversion that allows to call `as[A]` on any `Endpoint[Seq[String]]` to perform a
    * type conversion based on an implicit ``DecodeRequest[A]` which must be in scope.
    *
    * The resulting endpoint will fail when the result is non-empty and type conversion fails on one
    * or more of the elements in the `Seq`. It will succeed if the result is empty or type conversion
    * succeeds for all elements.
    */
  implicit class StringSeqEndpointOps(val self: Endpoint[Seq[String]]) {

    /* IMPLEMENTATION NOTE: This implicit class should extend AnyVal like all the other ones, to
     * avoid instance creation for each invocation of the extension method. However, this let's us
     * run into a compiler bug when we compile for Scala 2.10:
     * https://issues.scala-lang.org/browse/SI-8018. The bug is caused by the combination of four
     * things: 1) an implicit class, 2) extending AnyVal, 3) wrapping a class with type parameters,
     * 4) a partial function in the body. 2) is the only thing we can easily remove here, otherwise
     * we'd need to move the body of the method somewhere else. Once we drop support for Scala 2.10,
     * this class can safely extends AnyVal.
     */

    def as[A](implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Seq[A]] =
      self.mapAsync { items =>
        val decoded = items.map(d.apply)
        val errors = decoded.collect {
          case Throw(e) => Error.NotParsed(self.item, tag, e)
        }

        if (errors.isEmpty) Future.const(Try.collect(decoded))
        else Future.exception(Error.RequestErrors(errors))
      }
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `Endpoint[Option[String]]` to perform a
   * type conversion based on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting endpoint will fail when the result is non-empty and type conversion fails. It
   * will succeed if the result is empty or type conversion succeeds.
   */
  implicit class StringOptionEndpointOps(val self: Endpoint[Option[String]]) extends AnyVal {
    def as[A](implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Option[A]] =
      self.mapAsync {
        case Some(value) =>
          Future.const(d(value).rescue(notParsed[A](self, tag))).map(Some.apply)
        case None =>
          Future.None
      }
  }

  implicit class BufOptionEndpointOps(self: Endpoint[Option[Buf]]) {
    def as[A](implicit d: Decode.Json[A]): Endpoint[Option[A]] = new Endpoint[Option[A]] {
      // TODO: Will be better with StateT
      // See https://github.com/finagle/finch/pull/559
      def apply(input: Input): Endpoint.Result[Option[A]] = {
        val underlying = self.mapAsync {
          case Some(value) => Future.const(d(value, input.request.charsetOrUtf8)).map(Some.apply)
          case None => Future.None
        }

        underlying(input)
      }
    }
  }

  class GenericDerivation[A] {
    def fromParams[Repr <: HList](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      fp: FromParams[Repr]
    ): Endpoint[A] = fp.endpoint.map(gen.from)
  }

  /**
   * Generically derive a very basic instance of [[Endpoint]] for a given type `A`.
   */
  def derive[A]: GenericDerivation[A] = new GenericDerivation[A]

  implicit val endpointInstance: Alternative[Endpoint] = new Alternative[Endpoint] {

    override def ap[A, B](ff: Endpoint[A => B])(fa: Endpoint[A]): Endpoint[B] = ff.product(fa).map {
      case (f, a) => f(a)
    }

    override def map[A, B](fa: Endpoint[A])(f: A => B): Endpoint[B] = fa.map(f)

    override def product[A, B](fa: Endpoint[A], fb: Endpoint[B]): Endpoint[(A, B)] = fa.product(fb)

    override def pure[A](x: A): Endpoint[A] = new Endpoint[A] {
      override def apply(input: Input): Result[A] = Some(input -> Rerunnable(Output.payload(x)))
    }

    override def empty[A]: Endpoint[A] = Endpoint.empty[A]

    override def combineK[A](x: Endpoint[A], y: Endpoint[A]): Endpoint[A] = x | y
  }
}
