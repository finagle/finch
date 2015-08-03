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
   * Maps this [[Router]] to either `A => B` or `A => Future[B]`.
   */
  def apply(mapper: Mapper[A]): Router[mapper.Out] = mapper(self)

  /**
   * Extracts some value of type `A` from the given `input`.
   */
  def apply(input: Input): Option[(Input, () => Future[A])]

  /**
   * Maps this router to the given function `A => B`.
   */
  def map[B](fn: A => B): Router[B] = new Router[B] {
    def apply(input: Input): Option[(Input, () => Future[B])] =
      self(input).map {
        case (input, result) => (input, () => result().map(fn))
      }

    override def toString = self.toString
  }

  /**
   * Flat-maps the router to the given function `A => Future[B]`. If the given function `None` the resulting router will
   * also return `None`.
   */
  def embedFlatMap[B](fn: A => Future[B]): Router[B] = new Router[B] {
    def apply(input: Input): Option[(Input, () => Future[B])] =
      self(input).map {
        case (input, result) => (input, () => result().flatMap(fn))
      }

    override def toString = self.toString
  }

  /**
   * Flat-maps this router to the given function `A => Router[B]`.
   */
  def ap[B](fn: Router[A => B]): Router[B] = new Router[B] {
    def apply(input: Input): Option[(Input, () => Future[B])] =
      self(input).flatMap {
        case (input1, resultA) => fn(input1).map {
          case (input2, resultF) => (
            input2,
            () => resultA().join(resultF()).map {
              case (a, f) => f(a)
            }
          )
        }
      }

    override def toString = self.toString
  }

  /**
   * Composes this router with the given `that` router. The resulting router will succeed only if both this and `that`
   * routers succeed.
   */
  def /[B](that: Router[B])(implicit adjoin: PairAdjoin[A, B]): Router[adjoin.Out] =
    new Router[adjoin.Out] {
      val inner = self.ap(
        that.map { b => (a: A) => adjoin(a, b) }
      )
      def apply(input: Input): Option[(Input, () => Future[adjoin.Out])] = inner(input)

      override def toString = s"${self.toString}/${that.toString}"
    }

  /**
   * Composes this router with the given [[io.finch.request.RequestReader]].
   */
  def ?[B](that: RequestReader[B])(implicit adjoin: PairAdjoin[A, B]): Router[adjoin.Out] =
    new Router[adjoin.Out] {
      def apply(input: Input): Option[(Input, () => Future[adjoin.Out])] =
        self(input).map {
          case (input, result) => (
            input,
            () => result().join(that(input.request)).map {
              case (a, b) => adjoin(a, b)
            }
          )
        }

      override def toString = s"${self.toString}?${that.toString}"
    }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   */
  def |[B >: A](that: Router[B]): Router[B] = new Router[B] {
    def apply(input: Input): Option[(Input, () => Future[B])] = (self(input), that(input)) match {
      case (aa @ Some((a, _)), bb @ Some((b, _))) =>
        if (a.path.length <= b.path.length) aa else bb
      case (a, b) => a orElse b
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
   * Converts this router to a Finagle service from a request-like type `R` to a
   * [[com.twitter.finagle.httpx.Response]].
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
   * Creates an input for [[Router]] from [[com.twitter.finagle.httpx.Request]].
   */
  def Input(req: Request): Input = Input(req, req.path.split("/").toList.drop(1))

  /**
   * Creates a [[Router]] from the given [[Future]] `f`.
   */
  def const[A](f: Future[A]): Router[A] = embed(input => Some((input, () => f)))

  /**
   * Creates a [[Router]] from the given value `v`.
   */
  def value[A](v: A): Router[A] = const(Future.value(v))

  /**
   * Creates a [[Router]] from the given exception `exc`.
   */
  def exception[A](exc: Throwable): Router[A] = const(Future.exception(exc))

  /**
   * Creates a [[Router]] from the given function `Input => Output[A]`.
   */
  private[route] def embed[A](fn: Input => Option[(Input, () => Future[A])]): Router[A] = new Router[A] {
    def apply(input: Input): Option[(Input, () => Future[A])] = fn(input)
  }

  /**
   * An implicit conversion that turns any endpoint with an output type that can be converted into a response into a
   * service that returns responses.
   */
  @deprecated(message = "Endpoint is deprecated in favor of coproduct routers", since = "0.8.0")
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
    def apply(req: Req): Future[Rep] = router(Input(req)) match {
      case Some((input, result)) => result().flatMap(_(req))
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
