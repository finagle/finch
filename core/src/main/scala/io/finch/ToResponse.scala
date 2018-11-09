package io.finch

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf
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

trait LowPriorityToResponseInstances {
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

  protected[finch] def asyncResponseBuilder[A, CT <: String](writer: (A, Charset) => Buf)(implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[A], CT] = instance { (as, cs) =>
    val rep = Response()
    rep.setChunked(true)

    val writable = rep.writer

    as.foreachF(chunk => writable.write(writer(chunk, cs))).ensure(writable.close())
    rep.headerMap.setUnsafe("Content-Type", w.value)

    rep
  }

  private[finch] val NewLine: Buf = Buf.Utf8("\n")

  implicit def jsonAsyncStreamToResponse[A](implicit
    e: Encode.Json[A]
  ): Aux[AsyncStream[A], Application.Json] =
    asyncResponseBuilder[A, Application.Json]((a, cs) => e(a, cs).concat(NewLine))

  implicit def textAsyncStreamToResponse[A](implicit
    e: Encode.Text[A]
  ): Aux[AsyncStream[A], Text.Plain] =
    asyncResponseBuilder[A, Text.Plain]((a, cs) => e(a, cs).concat(NewLine))
}

trait HighPriorityToResponseInstances extends LowPriorityToResponseInstances {

  implicit def asyncBufToResponse[CT <: String](implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[Buf], CT] = asyncResponseBuilder[Buf, CT]((a, _) => a)

  implicit def responseToResponse[CT <: String]: Aux[Response, CT] = instance((r, _) => r)

  implicit def valueToResponse[A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): Aux[A, CT] = instance { (a, cs) =>
    val buf = e(a, cs)
    val rep = Response()

    if (!buf.isEmpty) {
      rep.content = buf
      rep.headerMap.setUnsafe("Content-Type", w.value)
    }

    rep
  }
}

object ToResponse extends HighPriorityToResponseInstances {

  /**
   * Enables server-driven content negotiation with client.
   *
   * Picks corresponding instance of `ToResponse` according to `Accept` header of a request
   */
  trait Negotiable[A, CT] {
    def apply(accept: Seq[Accept]): ToResponse.Aux[A, CT]
  }

  object Negotiable {

    implicit def coproductToNegotiable[A, CTH <: String, CTT <: Coproduct](implicit
      h: ToResponse.Aux[A, CTH],
      t: Negotiable[A, CTT],
      a: Accept.Matcher[CTH]
    ): Negotiable[A, CTH :+: CTT] = new Negotiable[A, CTH :+: CTT] {
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CTH :+: CTT] =
        if (accept.exists(_.matches[CTH])) h.asInstanceOf[ToResponse.Aux[A, CTH :+: CTT]]
        else t(accept).asInstanceOf[ToResponse.Aux[A, CTH :+: CTT]]
    }

    implicit def cnilToNegotiable[A, CTH <: String](implicit
      tr: ToResponse.Aux[A, CTH]
    ): Negotiable[A, CTH :+: CNil] = new Negotiable[A, CTH :+: CNil] {
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CTH :+: CNil] =
        tr.asInstanceOf[ToResponse.Aux[A, CTH :+: CNil]]
    }

    implicit def singleToNegotiable[A, CT <: String](implicit
      tr: ToResponse.Aux[A, CT]
    ): Negotiable[A, CT] = new Negotiable[A, CT] {
      def apply(accept: Seq[Accept]): ToResponse.Aux[A, CT] = tr
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
