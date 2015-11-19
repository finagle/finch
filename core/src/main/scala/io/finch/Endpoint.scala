package io.finch

import cats.Eval
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Cookie, Request, Response}
import com.twitter.util.Future
import io.finch.internal.{Mapper, PairAdjoin, ToService}
import shapeless._
import shapeless.ops.adjoin.Adjoin

/**
 * An `Endpoint` represents the HTTP endpoint.
 *
 * I is well known and widely adopted in Finagle that "Your Server as a Function" (i.e., `Request => Future[Response]`.
 * In a REST API setting this function may be viewed as `Request =1=> (A =2=> Future[B]) =3=> Future[Response]`, where
 * transformation `1` is request decoding (deserialization), transformation `2` - is business logic and transformation
 * `3` is- response encoding (serialization). The only interesting part here is transformation `2`
 * (i.e., `A => Future[B]`), which represents a bossiness logic of an application.
 *
 * An `Endpoint` transformation (`map`, `embedFlatMap`, etc.) encodes the business logic, while the rest of Finch
 * ecosystem takes care about both serialization and deserialization.
 *
 * A typical way to transform (or map) the `Endpoint` is to use [[Mapper]] and `Endpoint.apply` method, which, depending
 * on the argument type, delegates the map operation to the underlying function.
 *
 * {{{
 *   case class Foo(i: Int)
 *   case class Bar(s: String)
 *
 *   val foo: Endpoint[Foo] = get("foo") { Ok(Foo(42)) }
 *   val bar: Endpoint[Bar] = get("bar" / string) { s: String => Ok(Bar(s)) }
 * }}}
 *
 * `Endpoint`s are also composable in terms of or-else combinator (or a space invader `:+:`) that takes two `Endpoint`s
 * and gives a coproduct `Endpoint`.
 *
 * {{{
 *   val foobar: Endpoint[Foo :+: Bar :+: CNil] = foo :+: bar
 * }}}
 *
 * An `Endpoint` might be converted into a Finagle [[Service]] with `Endpoint.toService` method so it can be served with
 * Finagle HTTP.
 *
 * {{{
 *   Http.server.serve(foobar.toService)
 * }}}
 *
 */
trait Endpoint[A] { self =>
  /**
   * Maps this endpoint to either `A => Output[B]` or `A => Output[Future[B]]`.
   */
  def apply(mapper: Mapper[A]): Endpoint[mapper.Out] = mapper(self)

  /**
   * Extracts some value of type `A` from the given `input`.
   */
  // There is a reason why it can't be renamed to `run` as per https://github.com/finagle/finch/issues/371.
  // More details are here: http://stackoverflow.com/questions/32064375/magnet-pattern-and-overloaded-methods
  def apply(input: Input): Option[(Input, Eval[Future[Output[A]]])]

  /**
   * Maps this endpoint to the given function `A => B`.
   */
  def map[B](fn: A => B): Endpoint[B] =
    embedFlatMap(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Future[B]`.
   */
  def embedFlatMap[B](fn: A => Future[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, Eval[Future[Output[B]]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, output.map(f => f.flatMap(oa => oa.traverse(a => fn(a)))))
      }

    override def toString = self.toString
  }

  /**
   * Maps this endpoint to the given function `A => Output[B]`.
   */
  private[finch] def emap[B](fn: A => Output[B]): Endpoint[B] =
    femap(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Output[Future[B]]`.
   */
  private[finch] def efmap[B](fn: A => Output[Future[B]]): Endpoint[B] =
    femap(fn.andThen(ofb => ofb.traverse(identity)))

  /**
   * Maps this endpoint to the given function `A => Future[Output[B]]`.
   */
  private[finch] def femap[B](fn: A => Future[Output[B]]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, Eval[Future[Output[B]]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, output.map(f => f.flatMap { oa =>
            val fob = oa.traverse(fn).map(oob => oob.flatten)

            fob.map { ob =>
              val ob0 = ob.withContentType(ob.contentType.orElse(oa.contentType))
                          .withCharset(ob.charset.orElse(oa.charset))
              val ob1 = oa.headers.foldLeft(ob0)((acc, x) => acc.withHeader(x))
              val ob2 = oa.cookies.foldLeft(ob1)((acc, x) => acc.withCookie(x))

              ob2
            }
          }))
      }

    override def toString = self.toString
  }

  /**
   * Maps this endpoint to `Endpoint[A => B]`.
   */
  def ap[B](fn: Endpoint[A => B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, Eval[Future[Output[B]]])] =
      self(input).flatMap {
        case (remainder1, outputA) => fn(remainder1).map {
          case (remainder2, outputF) =>
            (remainder2, for { ofa <- outputA; off <- outputF } yield { ofa.join(off).map {
              case (oa, of) => oa.flatMap(a => of.map(f => f(a)))
            }})
        }
      }

    override def toString = self.toString
  }

  /**
   * Composes this endpoint with the given `that` endpoint. The resulting endpoint will succeed only
   * if both this and `that` endpoints succeed.
   */
  def /[B](that: Endpoint[B])(implicit adjoin: PairAdjoin[A, B]): Endpoint[adjoin.Out] =
    new Endpoint[adjoin.Out] {
      val inner = self.ap(
        that.map { b => (a: A) => adjoin(a, b) }
      )
      def apply(input: Input): Option[(Input, Eval[Future[Output[adjoin.Out]]])] = inner(input)

      override def toString = s"${self.toString}/${that.toString}"
    }

  /**
   * Composes this endpoint with the given [[RequestReader]].
   */
  def ?[B](that: RequestReader[B])(implicit adjoin: PairAdjoin[A, B]): Endpoint[adjoin.Out] =
    new Endpoint[adjoin.Out] {
      def apply(input: Input): Option[(Input, Eval[Future[Output[adjoin.Out]]])] =
        self(input).map {
          case (remainder, output) =>
            (remainder, output.map(f => f.join(that(input.request)).map {
              case (oa, b) => oa.map(a => adjoin(a, b))
            }))
        }

      override def toString = s"${self.toString}?${that.toString}"
    }

  /**
   * Sequentially composes this endpoint with the given `that` endpoint. The resulting router will
   * succeed if either this or `that` endpoints are succeed.
   */
  def |[B >: A](that: Endpoint[B]): Endpoint[B] = new Endpoint[B] {
    private[this] def aToB(o: Option[(Input, Eval[Future[Output[A]]])]): Option[(Input, Eval[Future[Output[B]]])] =
      o.map { case (r, oo) => (r, oo.map(_.asInstanceOf[Future[Output[B]]])) }

    def apply(input: Input): Option[(Input, Eval[Future[Output[B]]])] =
      (self(input), that(input)) match {
        case (aa @ Some((a, _)), bb @ Some((b, _))) =>
          if (a.path.length <= b.path.length) aToB(aa) else bb
        case (a, b) => aToB(a).orElse(b)
      }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  // A workaround for https://issues.scala-lang.org/browse/SI-1336
  def withFilter(p: A => Boolean): Endpoint[A] = self

  /**
   * Composes this endpoint with another in such a way that coproducts are flattened.
   */
  def :+:[B](that: Endpoint[B])(implicit adjoin: Adjoin[B :+: A :+: CNil]): Endpoint[adjoin.Out] =
    that.map(b => adjoin(Inl[B, A :+: CNil](b))) |
    self.map(a => adjoin(Inr[B, A :+: CNil](Inl[A, CNil](a))))

  def withHeader(header: (String, String)): Endpoint[A] = withOutput(o => o.withHeader(header))
  def withContentType(contentType: Option[String]): Endpoint[A] = withOutput(o => o.withContentType(contentType))
  def withCharset(charset: Option[String]): Endpoint[A] = withOutput(o => o.withCharset(charset))
  def withCookie(cookie: Cookie): Endpoint[A] = withOutput(o => o.withCookie(cookie))

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]`.
   */
  def toService(implicit ts: ToService[A]): Service[Request, Response] = ts(this)

  /**
   * Recovers from any exception occurred in this endpoint by creating a new endpoint that will handle any matching
   * throwable from the underlying future.
   */
  def rescue[B >: A](pf: PartialFunction[Throwable, Future[Output[B]]]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, Eval[Future[Output[B]]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, output.map(f => f.rescue(pf)))
      }

    override def toString = self.toString
  }

  /**
   * Recovers from any exception occurred in this endpoint by creating a new endpoint that will handle any matching
   * throwable from the underlying future.
   */
  def handle[B >: A](pf: PartialFunction[Throwable, Output[B]]): Endpoint[B] = rescue(pf.andThen(Future.value))

  private[this] def withOutput[B](fn: Output[A] => Output[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, Eval[Future[Output[B]]])] =
      self(input).map {
        case (remainder, output) => (remainder, output.map(f => f.map(o => fn(o))))
      }

    override def toString = self.toString
  }
}

/**
 * Provides extension methods for [[Endpoint]] to support coproduct and path syntax.
 */
object Endpoint {

  /**
   * Creates an [[Endpoint]] from the given [[Output]].
   */
  def apply(mapper: Mapper[HNil]): Endpoint[mapper.Out] = mapper(/)

  final implicit class ValueEndpointOps[B](val self: Endpoint[B]) extends AnyVal {
    /**
     * Converts this endpoint to one that returns any type with `B :: HNil` as its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, B :: HNil]): Endpoint[A] = self.map(value => gen.from(value :: HNil))
  }

  final implicit class HListEndpointOps[L <: HList](val self: Endpoint[L]) extends AnyVal {
    /**
     * Converts this endpoint to one that returns any type with this [[shapeless.HList]] as its representation.
     */
    def as[A](implicit gen: Generic.Aux[A, L]): Endpoint[A] = self.map(gen.from)
  }
}
