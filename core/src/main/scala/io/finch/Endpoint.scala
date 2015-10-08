package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Cookie, Status, Request, Response}
import com.twitter.util.Future
import io.finch.request._
import shapeless._
import shapeless.ops.adjoin.Adjoin
import shapeless.ops.function.FnToProduct

/**
 * An endpoint that is given an [[Endpoint.Input]], extracts some value of the type `A`, wrapped with
 * [[Endpoint.Output]].
 */
trait Endpoint[A] { self =>
  import Endpoint._

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
          (remainder, () => output().flatMap { oa =>
            val fb = fn(oa.value)

            fb.map { b =>
              oa.copy(value = b)
            }
          })
      }

    override def toString = self.toString
    override def errorHandler = self.errorHandler
  }

  /**
   * Maps this endpoint to the given function `A => Output[B]`.
   */
  private[finch] def emap[B](fn: A => Output[B]): Endpoint[B] =
    efmap(fn.andThen(o => o.copy(value = Future.value(o.value))))

  /**
   * Maps this endpoint to the given function `A => Output[Future[B]]`.
   */
  private[finch] def efmap[B](fn: A => Output[Future[B]]): Endpoint[B] =
    femap(fn.andThen(ofb => ofb.value.map(b => ofb.copy(value = b))))

  /**
   * Maps this endpoint to the given function `A => Future[Output[B]]`.
   */
  private[finch] def femap[B](fn: A => Future[Output[B]]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, () => output().flatMap { oa =>
            val fob = fn(oa.value)

            fob.map { ob =>
              ob.copy(
                headers = oa.headers ++ ob.headers,
                cookies = oa.cookies ++ ob.cookies,
                contentType = oa.contentType.orElse(ob.contentType),
                charset = oa.charset.orElse(ob.charset)
              )
            }
          })
      }

    override def toString = self.toString
    override def errorHandler = self.errorHandler
  }

  /**
   * Maps this endpoint to `Endpoint[A => B]`.
   */
  def ap[B](fn: Endpoint[A => B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).flatMap {
        case (remainder1, outputA) => fn(remainder1).map {
          case (remainder2, outputF) =>
            (
              remainder2,
              () => outputA().join(outputF()).map {
                case (oa, of) =>
                  val ob = of.copy(value = of.value(oa.value))
                  ob.copy(
                    headers = oa.headers ++ of.headers,
                    cookies = oa.cookies ++ of.cookies,
                    contentType = oa.contentType.orElse(of.contentType),
                    charset = oa.charset.orElse(of.charset)
                  )
              }
            )
        }
      }

    override def toString = self.toString
    override def errorHandler = self.errorHandler
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
      override def errorHandler = self.errorHandler.orElse(that.errorHandler)
    }

  /**
   * Composes this endpoint with the given [[RequestReader]].
   */
  def ?[B](that: RequestReader[B])(implicit adjoin: PairAdjoin[A, B]): Endpoint[adjoin.Out] =
    new Endpoint[adjoin.Out] {
      def apply(input: Input): Option[(Input, () => Future[Output[adjoin.Out]])] =
        self(input).map {
          case (remainder, output) =>
            (
              remainder,
              () =>  output().join(that(input.request)).map {
                case (a, b) => a.copy(value = adjoin(a.value, b))
              }
            )
        }

      override def toString = s"${self.toString}?${that.toString}"
      override def errorHandler = self.errorHandler
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
    override def errorHandler = self.errorHandler.orElse(that.errorHandler)
  }

  // A workaround for https://issues.scala-lang.org/browse/SI-1336
  def withFilter(p: A => Boolean): Endpoint[A] = self

  /**
   * Composes this endpoint with another in such a way that coproducts are flattened.
   */
  def :+:[B](that: Endpoint[B])(implicit adjoin: Adjoin[B :+: A :+: CNil]): Endpoint[adjoin.Out] =
    that.map(b => adjoin(Inl[B, A :+: CNil](b))) |
    self.map(a => adjoin(Inr[B, A :+: CNil](Inl[A, CNil](a))))

  def withHeader(header: (String, String)): Endpoint[A] = withOutput { o =>
    o.copy(headers = o.headers + header)
  }

  def withContentType(contentType: Option[String]): Endpoint[A] = withOutput { o =>
    o.copy(contentType = contentType)
  }

  def withCharset(charset: Option[String]): Endpoint[A] = withOutput { o =>
    o.copy(charset = charset)
  }

  def withCookie(cookie: Cookie): Endpoint[A] = withOutput { o =>
    o.copy(cookies = o.cookies :+ cookie)
  }

  /**
   * Converts this endpoint to a Finagle service `Request => Future[Response]`.
   */
  def toService(implicit ts: ToService[A]): Service[Request, Response] = ts(this)

  /**
   * Rescues from any exception occurred in this endpoint.
   */
  def rescue[B >: A](pf: PartialFunction[Throwable, Future[Output[B]]]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) =>
          (remainder, () => output().rescue(pf))
      }

    override def toString = self.toString
    override def errorHandler = self.errorHandler
  }

  // An error handler.
  private[finch] def errorHandler: PartialFunction[Throwable, Response] =
    PartialFunction.empty[Throwable, Response]

  /**
   * Handles any exception occurred this this endpoint.
   */
  def handle(pf: PartialFunction[Throwable, Response]): Endpoint[A] = new Endpoint[A] {
    def apply(input: Input): Option[(Input, () => Future[Output[A]])] = self(input)

    override def toString = self.toString
    override def errorHandler = self.errorHandler.orElse(pf)
  }

  private[this] def withOutput[B](fn: Output[A] => Output[B]): Endpoint[B] = new Endpoint[B] {
    def apply(input: Input): Option[(Input, () => Future[Output[B]])] =
      self(input).map {
        case (remainder, output) => (remainder, () => output().map(o => fn(o)))
      }

    override def toString = self.toString
    override def errorHandler = self.errorHandler
  }
}

/**
 * Provides extension methods for [[Endpoint]] to support coproduct and path syntax.
 */
object Endpoint {

  /**
   * An input for [[Endpoint]].
   */
  final case class Input(request: Request, path: Seq[String]) {
    def headOption: Option[String] = path.headOption
    def drop(n: Int): Input = copy(path = path.drop(n))
    def isEmpty: Boolean = path.isEmpty
  }

  /**
   * Creates an input for [[Endpoint]] from [[Request]].
   */
  def Input(req: Request): Input = Input(req, req.path.split("/").toList.drop(1))

  /**
   * An output of [[Endpoint]].
   */
  final case class Output[+A](
    value: A,
    status: Status = Status.Ok,
    headers: Map[String, String] = Map.empty[String, String],
    cookies: Seq[Cookie] = Seq.empty[Cookie],
    contentType: Option[String] = None,
    charset: Option[String] = None
  ) {
    def apply[B](value: B): Output[B] = copy(value = value)
    def withHeader(header: (String, String)): Output[A] = copy(headers = headers + header)
    def withCookie(cookie: Cookie): Output[A] = copy(cookies = cookies :+ cookie)
    def withContentType(contentType: Option[String]): Output[A] = copy(contentType = contentType)
    def withCharset(charset: Option[String]): Output[A] = copy(charset = charset)
  }

  object Output {
    val HNil: Output[HNil] = Output(shapeless.HNil)
  }

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
