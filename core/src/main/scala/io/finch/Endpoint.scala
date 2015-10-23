package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Cookie, Request, Response}
import com.twitter.util.Future
import io.finch.request._
import shapeless._
import shapeless.ops.adjoin.Adjoin
import shapeless.ops.function.FnToProduct

/**
 * An endpoint that is given an [[Input]], extracts some value of the type `A`, wrapped with [[Output]].
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
  def apply(input: Input): Option[(Input, () => Future[Output[A]])]

  /**
   * Maps this endpoint to the given function `A => B`.
   */
  def map[B](fn: A => B): Endpoint[B] =
    embedFlatMap(fn.andThen(Future.value))

  /**
   * Maps this endpoint to the given function `A => Future[B]`.
   */
  def embedFlatMap[B](fn: A => Future[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, () => output().flatMap(oa => oa.traverse(a => fn(a))))
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
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, () => output().flatMap { oa =>
            val fob = oa.traverse(fn).map(oob => oob.flatten)

            fob.map { ob =>
              val ob0 = ob.withContentType(oa.contentType.orElse(ob.contentType))
                          .withCharset(oa.charset.orElse(ob.charset))
              val ob1 = oa.headers.foldLeft(ob0)((acc, x) => acc.withHeader(x))
              val ob2 = oa.cookies.foldLeft(ob1)((acc, x) => acc.withCookie(x))

              ob2
            }
          })
      }

    override def toString = self.toString
  }

  /**
   * Maps this endpoint to `Endpoint[A => B]`.
   */
  def ap[B](fn: Endpoint[A => B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).flatMap {
        case (remainder1, outputA) => fn(remainder1).map {
          case (remainder2, outputF) =>
            (remainder2, () => outputA().join(outputF()).map {
              case (oa, of) => oa.flatMap(a => of.map(f => f(a)))
            })
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
      def apply(input: Input): Option[(Input, () => Future[Output[adjoin.Out]])] = inner(input)

      override def toString = s"${self.toString}/${that.toString}"
    }

  /**
   * Composes this endpoint with the given [[RequestReader]].
   */
  def ?[B](that: RequestReader[B])(implicit adjoin: PairAdjoin[A, B]): Endpoint[adjoin.Out] =
    new Endpoint[adjoin.Out] {
      def apply(input: Input): Option[(Input, () => Future[Output[adjoin.Out]])] =
        self(input).map {
          case (remainder, output) =>
            (remainder, () =>  output().join(that(input.request)).map {
                case (oa, b) => oa.map(a => adjoin(a, b))
            })
        }

      override def toString = s"${self.toString}?${that.toString}"
    }

  /**
   * Sequentially composes this endpoint with the given `that` endpoint. The resulting router will
   * succeed if either this or `that` endpoints are succeed.
   */
  def |[B >: A](that: Endpoint[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      (self(input), that(input)) match {
        case (aa @ Some((a, _)), bb @ Some((b, _))) =>
          if (a.path.length <= b.path.length) aa else bb
        case (a, b) => a orElse b
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
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, () => output().rescue(pf))
      }

    override def toString = self.toString
  }

  /**
   * Recovers from any exception occurred in this endpoint by creating a new endpoint that will handle any matching
   * throwable from the underlying future.
   */
  def handle[B >: A](pf: PartialFunction[Throwable, Output[B]]): Endpoint[B] = rescue(pf.andThen(Future.value))

  private[this] def withOutput[B](fn: Output[A] => Output[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) => (remainder, () => output().map(o => fn(o)))
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
  def apply[A](mapper: Mapper[HNil]): Endpoint[mapper.Out] = mapper(/)

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of one argument.
   */
  @deprecated("Use smart apply (Endpoint.apply) instead", "0.8.5")
  implicit class RArrow1[A](r: Endpoint[A]) {
    def />[B](fn: A => B): Endpoint[B] = r.map(fn)
    def />>[B](fn: A => Future[B]): Endpoint[B] = r.embedFlatMap(fn)
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with values.
   */
  @deprecated("Use smart apply (Endpoint.apply) instead", "0.8.5")
  implicit class RArrow0(r: Endpoint0) {
    def />[B](v: => B): Endpoint[B] = r.map(_ => v)
    def />>[B](v: => Future[B]): Endpoint[B] = r.embedFlatMap(_ => v)
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of two arguments.
   */
  @deprecated("Use smart apply (Endpoint.apply) instead", "0.8.5")
  implicit class RArrow2[A, B](r: Endpoint2[A, B]) {
    def />[C](fn: (A, B) => C): Endpoint[C] = r.map {
      case a :: b :: HNil => fn(a, b)
    }

    def />>[C](fn: (A, B) => Future[C]): Endpoint[C] = r.embedFlatMap {
      case a :: b :: HNil => fn(a, b)
    }
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of three arguments.
   */
  @deprecated("Use smart apply (Endpoint.apply) instead", "0.8.5")
  implicit class RArrow3[A, B, C](r: Endpoint3[A, B, C]) {
    def />[D](fn: (A, B, C) => D): Endpoint[D] = r.map {
      case a :: b :: c :: HNil => fn(a, b, c)
    }

    def />>[D](fn: (A, B, C) => Future[D]): Endpoint[D] = r.embedFlatMap {
      case a :: b :: c :: HNil => fn(a, b, c)
    }
  }

  /**
   * Add `/>` and `/>>` compositors to `Router` to compose it with function of N arguments.
   */
  @deprecated("Use smart apply (Endpoint.apply) instead", "0.8.5")
  implicit class RArrowN[L <: HList](r: Endpoint[L]) {
    def />[F, I](fn: F)(implicit ftp: FnToProduct.Aux[F, L => I]): Endpoint[I] =
      r.map(ftp(fn))

    def />>[F, I, FI](fn: F)(
      implicit ftp: FnToProduct.Aux[F, L => FI], ev: FI <:< Future[I]
    ): Endpoint[I] = r.embedFlatMap(value => ev(ftp(fn)(value)))
  }
}
