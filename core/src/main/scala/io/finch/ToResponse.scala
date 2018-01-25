package io.finch

import cats.syntax.eq._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf
import java.nio.charset.Charset
import scala.annotation.implicitNotFound
import shapeless._

/**
 * Represents a conversion from `A` to [[Response]].
 */
trait ToResponse[A, CT] {

  def apply(a: A, cs: Charset, accept: Seq[Accept]): Response
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
  type Aux[A, CT] = ToResponse[A, CT]

  def instance[A, CT](fn: (A, Charset, Seq[Accept]) => Response): Aux[A, CT] = new ToResponse[A, CT] {
    def apply(a: A, cs: Charset, accept: Seq[Accept]): Response = fn(a, cs, accept)
  }

  protected[finch] def asyncResponseBuilder[A, CT <: String](writer: (A, Charset) => Buf)(implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[A], CT] = instance { (as, cs, _) =>
    val rep = Response()
    rep.setChunked(true)

    val writable = rep.writer

    as.foreachF(chunk => writable.write(writer(chunk, cs))).ensure(writable.close())
    rep.contentType = w.value

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

  implicit def coproductCtToResponse[A, CT <: String, CTT <: Coproduct](implicit
    h: ToResponse[A, CT],
    t: ToResponse[A, CTT],
    w: Witness.Aux[CT]
  ): ToResponse[A, CT :+: CTT] = instance { (a, ch, accept) =>
    Accept.fromString(w.value) match {
      case Some(ct) if accept.isEmpty || accept.exists(_ === ct) =>
        h(a, ch, accept)
      case _ =>
        t(a, ch, accept)
    }
  }

  implicit def cnilCtToResponse[A, CT <: String](implicit
    tr: ToResponse[A, CT]
  ): ToResponse[A, CT :+: CNil] = instance { (a, ch, accept) =>
    tr(a, ch, accept)
  }
}

trait HighPriorityToResponseInstances extends LowPriorityToResponseInstances {

  implicit def asyncBufToResponse[CT <: String](implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[Buf], CT] = asyncResponseBuilder[Buf, CT]((a, _) => a)

  implicit def responseToResponse[CT <: String]: Aux[Response, CT] = instance((r, _, _) => r)

  implicit def valueToResponse[A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): Aux[A, CT] = instance { (a, cs, _) =>
    val buf = e(a, cs)
    val rep = Response()

    if (!buf.isEmpty) {
      rep.content = buf
      rep.contentType = w.value
    }

    rep
  }
}

object ToResponse extends HighPriorityToResponseInstances {

  implicit def cnilToResponse[CT <: String]: Aux[CNil, CT] =
    instance((_, _, _) => Response(Version.Http10, Status.NotFound))

  implicit def coproductToResponse[H, T <: Coproduct, CT](implicit
    trH: ToResponse.Aux[H, CT],
    trT: ToResponse.Aux[T, CT]
  ): Aux[H :+: T, CT] = instance {
    case (Inl(h), cs, accept) => trH(h, cs, accept)
    case (Inr(t), cs, accept) => trT(t, cs, accept)
  }
}
