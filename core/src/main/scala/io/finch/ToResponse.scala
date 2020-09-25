package io.finch

import java.nio.charset.Charset

import scala.annotation.implicitNotFound

import cats.{Applicative, Functor}
import com.twitter.finagle.http.{Response, Status, Version}
import shapeless._

/**
  * Represents a conversion from `A` to [[Response]].
  */
trait ToResponse[F[_], A] {
  type ContentType

  def apply(a: A, cs: Charset): F[Response]
}

trait ToResponseInstances {

  @implicitNotFound(
    """An Endpoint you're trying to convert into a Finagle service is missing one or more encoders.

  Make sure ${A} is one of the following:

  * A com.twitter.finagle.http.Response
  * A value of a type with an io.finch.Encode instance (with the corresponding content-type)
  * A coproduct made up of some combination of the above

  See https://github.com/finagle/finch/blob/master/docs/src/main/tut/cookbook.md#fixing-the-toservice-compile-error
"""
  )
  type Aux[F[_], A, CT] = ToResponse[F, A] { type ContentType = CT }

  def instance[F[_], A, CT](fn: (A, Charset) => F[Response]): Aux[F, A, CT] = new ToResponse[F, A] {
    type ContentType = CT
    def apply(a: A, cs: Charset): F[Response] = fn(a, cs)
  }

  implicit def responseToResponse[F[_], CT <: String](implicit
      F: Applicative[F]
  ): Aux[F, Response, CT] = instance((r, _) => F.pure(r))

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

  /**
    * Enables server-driven content negotiation with client.
    *
    * Picks corresponding instance of `ToResponse` according to `Accept` header of a request
    */
  trait Negotiable[F[_], A, CT] {
    def apply(accept: List[Accept]): ToResponse.Aux[F, A, CT]
  }

  object Negotiable {

    implicit def coproductToNegotiable[F[_], A, CTH <: String, CTT <: Coproduct](implicit
        h: ToResponse.Aux[F, A, CTH],
        t: Negotiable[F, A, CTT],
        a: Accept.Matcher[CTH]
    ): Negotiable[F, A, CTH :+: CTT] = new Negotiable[F, A, CTH :+: CTT] {
      def apply(accept: List[Accept]): ToResponse.Aux[F, A, CTH :+: CTT] =
        if (accept.exists(_.matches[CTH])) h.asInstanceOf[ToResponse.Aux[F, A, CTH :+: CTT]]
        else t(accept).asInstanceOf[ToResponse.Aux[F, A, CTH :+: CTT]]
    }

    implicit def cnilToNegotiable[F[_], A, CTH <: String](implicit
        tr: ToResponse.Aux[F, A, CTH]
    ): Negotiable[F, A, CTH :+: CNil] = new Negotiable[F, A, CTH :+: CNil] {
      def apply(accept: List[Accept]): ToResponse.Aux[F, A, CTH :+: CNil] =
        tr.asInstanceOf[ToResponse.Aux[F, A, CTH :+: CNil]]
    }

    implicit def singleToNegotiable[F[_], A, CT <: String](implicit
        tr: ToResponse.Aux[F, A, CT]
    ): Negotiable[F, A, CT] = new Negotiable[F, A, CT] {
      def apply(accept: List[Accept]): ToResponse.Aux[F, A, CT] = tr
    }
  }

  trait FromCoproduct[F[_], C <: Coproduct] extends ToResponse[F, C]

  object FromCoproduct {
    type Aux[F[_], C <: Coproduct, CT] = FromCoproduct[F, C] { type ContentType = CT }

    def instance[F[_], C <: Coproduct, CT <: String](fn: (C, Charset) => F[Response]): Aux[F, C, CT] =
      new FromCoproduct[F, C] {
        type ContentType = CT
        def apply(c: C, cs: Charset): F[Response] = fn(c, cs)
      }

    implicit def cnilToResponse[F[_], CT <: String](implicit
        F: Applicative[F]
    ): Aux[F, CNil, CT] = instance((_, _) => F.pure(Response(Version.Http10, Status.NotFound)))

    implicit def cconsToResponse[F[_], L, R <: Coproduct, CT <: String](implicit
        trL: ToResponse.Aux[F, L, CT],
        fcR: Aux[F, R, CT]
    ): Aux[F, L :+: R, CT] = instance {
      case (Inl(h), cs) => trL(h, cs)
      case (Inr(t), cs) => fcR(t, cs)
    }
  }

  implicit def coproductToResponse[F[_], C <: Coproduct, CT <: String](implicit
      fc: FromCoproduct.Aux[F, C, CT]
  ): Aux[F, C, CT] = fc
}
