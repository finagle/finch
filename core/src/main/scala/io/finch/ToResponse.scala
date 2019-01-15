package io.finch

import com.twitter.finagle.http.{Response, Status, Version}
import java.nio.charset.Charset
import scala.annotation.implicitNotFound
import shapeless._

/**
 * Represents a conversion from `A` to [[Response]].
 */
trait ToResponse[A] {
  type ContentType

  def apply(a: A, cs: Charset): Response
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
  type Aux[A, CT] = ToResponse[A] { type ContentType = CT }

  def instance[A, CT](fn: (A, Charset) => Response): Aux[A, CT] = new ToResponse[A] {
    type ContentType = CT
    def apply(a: A, cs: Charset): Response = fn(a, cs)
  }

  implicit def responseToResponse[CT <: String]: Aux[Response, CT] = instance((r, _) => r)

  implicit def valueToResponse[A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): Aux[A, CT] = instance { (a, cs) =>
    val buf = e(a, cs)
    val rep = Response(Version.Http11, Status.Ok)

    if (!buf.isEmpty) {
      rep.content = buf
      rep.headerMap.setUnsafe("Content-Type", w.value)
    }

    rep
  }

  implicit def streamToResponse[S[_[_], _], F[_], A, CT <: String](implicit
    E: EncodeStream.Aux[S, F, A, CT],
    W: Witness.Aux[CT]
  ): Aux[S[F, A], CT] = instance { (a, cs) =>
    val stream = E(a, cs)
    val rep = Response(Version.Http11, Status.Ok, stream)
    rep.headerMap.setUnsafe("Content-Type", W.value)
    rep.headerMap.setUnsafe("Transfer-Encoding", "chunked")

    rep
  }
}

object ToResponse extends ToResponseInstances {

  /**
   * Enables server-driven content negotiation with client.
   *
   * Picks corresponding instance of `ToResponse` according to `Accept` header of a request
   */
  trait Negotiable[A, CT] {
    def apply(accept: List[Accept]): ToResponse.Aux[A, CT]
  }

  object Negotiable {

    implicit def coproductToNegotiable[A, CTH <: String, CTT <: Coproduct](implicit
      h: ToResponse.Aux[A, CTH],
      t: Negotiable[A, CTT],
      a: Accept.Matcher[CTH]
    ): Negotiable[A, CTH :+: CTT] = new Negotiable[A, CTH :+: CTT] {
      def apply(accept: List[Accept]): ToResponse.Aux[A, CTH :+: CTT] =
        if (accept.exists(_.matches[CTH])) h.asInstanceOf[ToResponse.Aux[A, CTH :+: CTT]]
        else t(accept).asInstanceOf[ToResponse.Aux[A, CTH :+: CTT]]
    }

    implicit def cnilToNegotiable[A, CTH <: String](implicit
      tr: ToResponse.Aux[A, CTH]
    ): Negotiable[A, CTH :+: CNil] = new Negotiable[A, CTH :+: CNil] {
      def apply(accept: List[Accept]): ToResponse.Aux[A, CTH :+: CNil] =
        tr.asInstanceOf[ToResponse.Aux[A, CTH :+: CNil]]
    }

    implicit def singleToNegotiable[A, CT <: String](implicit
      tr: ToResponse.Aux[A, CT]
    ): Negotiable[A, CT] = new Negotiable[A, CT] {
      def apply(accept: List[Accept]): ToResponse.Aux[A, CT] = tr
    }
  }

  trait FromCoproduct[C <: Coproduct] extends ToResponse[C]

  object FromCoproduct {
    type Aux[C <: Coproduct, CT] = FromCoproduct[C] { type ContentType = CT }

    def instance[C <: Coproduct, CT <: String](fn: (C, Charset) => Response): Aux[C, CT] =
      new FromCoproduct[C] {
        type ContentType = CT
        def apply(c: C, cs: Charset): Response = fn(c, cs)
      }

    implicit def cnilToResponse[CT <: String]: Aux[CNil, CT] =
      instance((_, _) => Response(Version.Http10, Status.NotFound))

    implicit def cconsToResponse[L, R <: Coproduct, CT <: String](
      implicit trL: ToResponse.Aux[L, CT], fcR: Aux[R, CT]
    ): Aux[L :+: R, CT] = instance {
      case (Inl(h), cs) => trL(h, cs)
      case (Inr(t), cs) => fcR(t, cs)
    }
  }

  implicit def coproductToResponse[C <: Coproduct, CT <: String](
    implicit fc: FromCoproduct.Aux[C, CT]
  ): Aux[C, CT] = fc
}
