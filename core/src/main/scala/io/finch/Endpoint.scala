package io.finch

import scala.reflect.ClassTag

import cats.{Alternative, Eval}
import cats.data.StateT
import cats.std.option._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Cookie, Request, Response}
import com.twitter.util.{Future, Return, Throw, Try}
import io.finch.internal.{FromParams, Mapper, PairAdjoin, ToService}
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
abstract class Endpoint[A] extends Serializable { self =>

  /**
   * The [[StateT]] instance underneath this endpoint.
   */
  def embed: Endpoint.State[A]

  /**
   * Request item (part) that's this endpoint work with.
   */
  def item: items.RequestItem = items.MultipleItems

  /**
   * Maps this endpoint to either `A => Output[B]` or `A => Output[Future[B]]`.
   */
  final def apply(mapper: Mapper[A]): Endpoint[mapper.Out] = mapper(this)

  // There is a reason why `apply` can't be renamed to `run` as per
  //   https://github.com/finagle/finch/issues/371.
  // More details are here:
  //   http://stackoverflow.com/questions/32064375/magnet-pattern-and-overloaded-methods

  /**
   * Runs this endpoint on the given `input`.
   */
  final def apply(input: Input): Endpoint.Result[A] = embed.run(input)

  /**
   * Maps this endpoint to the given function `A => B`.
   */
  final def map[B](fn: A => B): Endpoint[B] =
    mapAsync(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Future[B]`.
   */
  final def mapAsync[B](fn: A => Future[B]): Endpoint[B] =
    modify(embed.map(o => o.map(f => f.flatMap(oa => oa.traverse(a => fn(a))))))

  /**
   * Maps this endpoint to the given function `A => Output[B]`.
   */
  final def mapOutput[B](fn: A => Output[B]): Endpoint[B] =
    mapOutputAsync(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Future[Output[B]]`.
   */
  final def mapOutputAsync[B](fn: A => Future[Output[B]]): Endpoint[B] =
    modify(embed.map(o => o.map(f => f.flatMap { oa =>
      val fob = oa.traverse(fn).map(oob => oob.flatten)

      fob.map { ob =>
        val ob1 = oa.headers.foldLeft(ob)((acc, x) => acc.withHeader(x))
        val ob2 = oa.cookies.foldLeft(ob1)((acc, x) => acc.withCookie(x))

        ob2
      }
    })))

  /**
   * Transforms this endpoint to the given function `Future[Output[A]] => Future[Output[B]]`.
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
    modify(embed.map(o => o.map(fn)))

  /**
   * Composes this endpoint with the given `that` endpoint into an endpoint of product (tuple).
   */
  final def product[B](that: Endpoint[B]): Endpoint[(A, B)] = {
    def join(
      foa: Future[Output[A]],
      fob: Future[Output[B]]
    ): Future[Output[(A, B)]] = Future.join(foa.liftToTry, fob.liftToTry).flatMap {
      case (Return(oa), Return(ob)) => Future.value(oa.flatMap(a => ob.map(b => (a, b))))
      case (Throw(oa), Throw(ob)) => Future.exception(collectExceptions(oa, ob))
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

    modify(
      for { oa <- self.embed; ob <- that.embed }
      yield oa.flatMap(ofa => ob.map(off => join(ofa, off)))
    )
  }

  /**
   * Maps this endpoint to `Endpoint[A => B]`.
   */
  @deprecated("Use product or Applicative[Endpoint].ap", "0.11.0")
  final def ap[B](fn: Endpoint[A => B]): Endpoint[B] = product(fn).map {
    case (a, f) => f(a)
  }

  /**
   * Composes this endpoint with the given `that` endpoint. The resulting endpoint will succeed only
   * if both this and `that` endpoints succeed.
   */
  final def adjoin[B](that: Endpoint[B])(implicit pa: PairAdjoin[A, B]): Endpoint[pa.Out] =
    new Endpoint[pa.Out] {
      override val embed: Endpoint.State[pa.Out] =
        self.product(that).map { case (a, b) => pa(a, b) }.embed
      override val item: items.RequestItem = self.item
      override def toString: String = s"${self.toString} :: ${that.toString}"
    }

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  @deprecated("Use :: instead", "0.11")
  final def ?[B](that: Endpoint[B])(implicit pa: PairAdjoin[A, B]): Endpoint[pa.Out] =
    self.adjoin(that)

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  @deprecated("Use :: instead", "0.11")
  final def /[B](that: Endpoint[B])(implicit pa: PairAdjoin[A, B]): Endpoint[pa.Out] =
    self.adjoin(that)

  /**
   * Composes this endpoint with the given [[Endpoint]].
   */
  final def ::[B](that: Endpoint[B])(implicit pa: PairAdjoin[B, A]): Endpoint[pa.Out] =
    that.adjoin(self)

  /**
   * Sequentially composes this endpoint with the given `that` endpoint. The resulting endpoint will
   * succeed if either this or `that` endpoints are succeed.
   */
  final def |[B >: A](that: Endpoint[B]): Endpoint[B] = new Endpoint[B] {
    private[this] def aToB(o: Endpoint.Result[A]): Endpoint.Result[B] =
      o.map { case (r, oo) => (r, oo.map(_.asInstanceOf[Future[Output[B]]])) }

    override val embed: Endpoint.State[B] = StateT(i => (self(i), that(i)) match {
      case (aa @ Some((a, _)), bb @ Some((b, _))) =>
        if (a.path.length <= b.path.length) aToB(aa) else bb
      case (a, b) => aToB(a).orElse(b)
    })

    override val item: items.RequestItem = self.item
    override def toString: String = s"(${self.toString}|${that.toString})"
  }

  /**
   * Composes this endpoint with another in such a way that coproducts are flattened.
   */
  final def :+:[B](that: Endpoint[B])(implicit adjoin: Adjoin[B :+: A :+: CNil]): Endpoint[adjoin.Out] =
    that.map(b => adjoin(Inl[B, A :+: CNil](b))) |
    self.map(a => adjoin(Inr[B, A :+: CNil](Inl[A, CNil](a))))

  final def withHeader(header: (String, String)): Endpoint[A] =
    withOutput(o => o.withHeader(header))

  final def withCookie(cookie: Cookie): Endpoint[A] =
    withOutput(o => o.withCookie(cookie))

  /**
   * Recovers from any exception occurred in this endpoint by creating a new endpoint that will
   * handle any matching throwable from the underlying future.
   */
  final def rescue[B >: A](pf: PartialFunction[Throwable, Future[Output[B]]]): Endpoint[B] =
    modify(embed.map(o => o.map(f => f.rescue(pf))))

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
    else Future.exception(Error.NotValid(item, "should " + rule))
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
   * to be reused across multiple request readers.
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
   * to be reused across multiple request readers.
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
  final def lift: Endpoint[Option[A]] =
    modify(embed.map(o => o.map(f => f.liftToTry.map(f =>
      f.toOption.fold(Output.None: Output[Option[A]])(o => o.map(Some.apply)))))
    )

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]`.
   */
  final def toService(implicit
    ts: ToService.Aux[A, Witness.`"application/json"`.T]
  ): Service[Request, Response] = ts(self)

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]`.
   */
  final def toServiceAs[CT <: String](implicit
    ts: ToService.Aux[A, CT]
  ): Service[Request, Response] = ts(self)

  private[this] def withOutput[B](fn: Output[A] => Output[B]): Endpoint[B] =
    modify(embed.map(o => o.map(f => f.map(o => fn(o)))))

  private[this] def modify[B](newRun: Endpoint.State[B]): Endpoint[B] =
    new Endpoint[B] {
      override val embed: Endpoint.State[B] = newRun
      override val item: items.RequestItem = self.item
      override def toString: String = self.toString
    }
}

/**
 * Provides extension methods for [[Endpoint]] to support coproduct and path syntax.
 */
object Endpoint {

  type State[A] = StateT[Option, Input, Eval[Future[Output[A]]]]
  type Result[A] = Option[(Input, Eval[Future[Output[A]]])]

  /**
   * Creates an [[Endpoint]] from the given [[Output]].
   */
  def apply(mapper: Mapper[shapeless.HNil]): Endpoint[mapper.Out] = mapper(/)

  private[finch] val Empty: Endpoint[HNil] = embed(items.MultipleItems)(input =>
    Some((input, Eval.now(Future.value(Output.payload(HNil: HNil)))))
  )

  private[finch] def embed[A](i: items.RequestItem)(f: Input => Result[A]): Endpoint[A] =
    new Endpoint[A] {
      override val embed: Endpoint.State[A] = StateT(f)
      override val item: items.RequestItem = i
      override def toString: String =
        s"${i.kind}${i.nameOption.map(n => "(" + n + ")").getOrElse("")}"
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
    def asTuple(implicit tupler: Tupler[L]): Endpoint[tupler.Out] = self.map(tupler(_))
  }

  private[this] def notParsed[A](
    e: Endpoint[_], tag: ClassTag[_]
  ): PartialFunction[Throwable, Try[A]] = {
    case exc => Throw[A](Error.NotParsed(e.item, tag, exc))
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `Endpoint[String]` to perform a type
   * conversion based on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when type conversion fails.
   */
  implicit class StringEndpointOps(val self: Endpoint[String]) extends AnyVal {
    def as[A](implicit decoder: Decode[A], tag: ClassTag[A]): Endpoint[A] =
      self.mapAsync(value => Future.const(decoder(value).rescue(notParsed[A](self, tag))))
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

    def as[A](implicit decoder: Decode[A], tag: ClassTag[A]): Endpoint[Seq[A]] =
      self.mapAsync { items =>
        val decoded = items.map(decoder.apply)
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
    def as[A](implicit decoder: Decode[A], tag: ClassTag[A]): Endpoint[Option[A]] =
      self.mapAsync {
        case Some(value) =>
          Future.const(decoder(value).rescue(notParsed[A](self, tag)).map(Some.apply))
        case None =>
          Future.None
      }

    private[finch] def noneIfEmpty: Endpoint[Option[String]] = self.map {
      case Some(value) if value.isEmpty => None
      case other => other
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

    override def ap[A, B](ff: Endpoint[A => B])(fa: Endpoint[A]): Endpoint[B] =
      ff.product(fa).map { case (f, a) => f(a) }

    override def map[A, B](fa: Endpoint[A])(f: A => B): Endpoint[B] =
      fa.map(f)

    override def product[A, B](fa: Endpoint[A], fb: Endpoint[B]): Endpoint[(A, B)] =
      fa.product(fb)

    override def pure[A](x: A): Endpoint[A] = new Endpoint[A] {
      override val embed: Endpoint.State[A] = StateT.pure(Eval.now(Future.value(Ok(x))))
    }

    override def pureEval[A](x: Eval[A]): Endpoint[A] = new Endpoint[A] {
      override val embed: Endpoint.State[A] = StateT.pure(x.map(out => Future.value(Ok(out))))
    }

    override def empty[A]: Endpoint[A] = new Endpoint[A] {
      override val embed: Endpoint.State[A] = StateT(_ => None: Endpoint.Result[A])
    }

    override def combineK[A](x: Endpoint[A], y: Endpoint[A]): Endpoint[A] = x | y
  }
}
