package io.finch

import cats.{Applicative, Functor}
import com.twitter.finagle.http.{Response, Status, Version}
import shapeless._

import java.nio.charset.Charset
import scala.annotation.implicitNotFound

/** Represents a conversion from `A` to [[com.twitter.finagle.http.Response]]. */
trait ToResponse[F[_], A] {
  type ContentType
  def apply(a: A, cs: Charset): F[Response]
}

trait ToResponseInstances {
  @implicitNotFound("""An Endpoint you're trying to convert into a Finagle service is missing one or more encoders.

  Make sure ${A} is one of the following:

  * A com.twitter.finagle.http.Response
  * A value of a type with an io.finch.Encode instance (with the corresponding content-type)
  * A coproduct made up of some combination of the above

  See https://github.com/finagle/finch/blob/master/docs/src/main/tut/cookbook.md#fixing-the-toservice-compile-error
  """)
  type Aux[F[_], A, CT] = ToResponse[F, A] {
    type ContentType = CT
  }

  def instance[F[_], A, CT](fn: (A, Charset) => F[Response]): Aux[F, A, CT] =
    new ToResponse[F, A] {
      type ContentType = CT
      def apply(a: A, cs: Charset): F[Response] = fn(a, cs)
    }

  implicit def responseToResponse[F[_], CT <: String](implicit F: Applicative[F]): Aux[F, Response, CT] =
    instance((rep, _) => F.pure(rep))

  implicit def valueToResponse[F[_], A, CT <: String](implicit
      F: Applicative[F],
      A: Encode.Aux[A, CT],
      CT: Witness.Aux[CT]
  ): Aux[F, A, CT] = instance { (a, cs) =>
    val buf = A(a, cs)
    val rep = Response(Version.Http11, Status.Ok)
    if (!buf.isEmpty) {
      rep.content = buf
      rep.headerMap.setUnsafe("Content-Type", CT.value)
    }

    F.pure(rep)
  }

  implicit def streamToResponse[F[_], S[_[_], _], A, CT <: String](implicit
      F: Functor[F],
      S: EncodeStream.Aux[F, S, A, CT],
      CT: Witness.Aux[CT]
  ): Aux[F, S[F, A], CT] = instance { (a, cs) =>
    F.map(S(a, cs)) { stream =>
      val rep = Response(Version.Http11, Status.Ok, stream)
      rep.headerMap.setUnsafe("Content-Type", CT.value)
      rep.headerMap.setUnsafe("Transfer-Encoding", "chunked")
      rep
    }
  }
}

object ToResponse extends ToResponseInstances {
  implicit def coproductToResponse[F[_], C <: Coproduct, CT](implicit
      fc: FromCoproduct.Aux[F, C, CT]
  ): Aux[F, C, CT] = fc

  final case class Negotiated[F[_], A](
      value: ToResponse[F, A],
      error: ToResponse[F, Exception],
      acceptable: Boolean = true
  )

  /** Enables server-driven content negotiation with client.
    *
    * Picks corresponding instance of `ToResponse` according to `Accept` header of a request.
    */
  trait Negotiable[F[_], A, CT] {
    def apply(accept: List[Accept]): Negotiated[F, A]
  }

  object Negotiable {
    implicit def coproductToNegotiable[F[_], A, CTH <: String, CTT <: Coproduct](implicit
        value: Aux[F, A, CTH],
        error: Aux[F, Exception, CTH],
        rest: Negotiable[F, A, CTT],
        matcher: Accept.Matcher[CTH]
    ): Negotiable[F, A, CTH :+: CTT] =
      accept => if (matcher(accept)) Negotiated(value, error) else rest(accept)

    implicit def cnilToNegotiable[F[_], A, CTH <: String](implicit
        value: Aux[F, A, CTH],
        error: Aux[F, Exception, CTH],
        matcher: Accept.Matcher[CTH]
    ): Negotiable[F, A, CTH :+: CNil] =
      accept => Negotiated(value, error, matcher(accept))

    implicit def singleToNegotiable[F[_], A, CT <: String](implicit
        value: Aux[F, A, CT],
        error: Aux[F, Exception, CT],
        matcher: Accept.Matcher[CT]
    ): Negotiable[F, A, CT] =
      accept => Negotiated(value, error, matcher(accept))
  }

  trait FromCoproduct[F[_], C <: Coproduct] extends ToResponse[F, C]
  object FromCoproduct {
    type Aux[F[_], C <: Coproduct, CT] = FromCoproduct[F, C] {
      type ContentType = CT
    }

    def instance[F[_], C <: Coproduct, CT](fn: (C, Charset) => F[Response]): Aux[F, C, CT] =
      new FromCoproduct[F, C] {
        type ContentType = CT
        def apply(c: C, cs: Charset): F[Response] = fn(c, cs)
      }

    implicit def cnilToResponse[F[_], CT](implicit F: Applicative[F]): Aux[F, CNil, CT] =
      instance((_, _) => F.pure(Response(Version.Http10, Status.NotFound)))

    implicit def cconsToResponse[F[_], L, R <: Coproduct, CT](implicit
        tr: ToResponse.Aux[F, L, CT],
        fc: Aux[F, R, CT]
    ): Aux[F, L :+: R, CT] = instance {
      case (Inl(l), cs) => tr(l, cs)
      case (Inr(r), cs) => fc(r, cs)
    }
  }
}
