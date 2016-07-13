package io.finch.internal

import java.nio.charset.Charset

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Response, Status, Version}
import com.twitter.io.Buf
import io.finch._
import shapeless._

/**
 * Represents a conversion from `A` to [[Response]].
 */
trait ToResponse[A] {
  type ContentType <: String

  def apply(a: A, cs: Charset): Response
}

trait LowPriorityToResponseInstances {
  type Aux[A, CT] = ToResponse[A] { type ContentType = CT }

  def instance[A, CT <: String](fn: (A, Charset) => Response): Aux[A, CT] = new ToResponse[A] {
    type ContentType = CT
    def apply(a: A, cs: Charset): Response = fn(a, cs)
  }

  private[this] def asyncResponseBuilder[A, CT <: String](writer: (A, Charset) => Buf)(implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[A], CT] = instance { (as, cs) =>
    val rep = Response()
    rep.setChunked(true)

    val writable = rep.writer

    as.foreachF(chunk => writable.write(writer(chunk, cs))).ensure(writable.close())
    rep.contentType = w.value

    rep
  }

  implicit def asyncBufToResponse[CT <: String](implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[Buf], CT] = asyncResponseBuilder((a, _) => a)

  private[this] val newLine: Buf = Buf.Utf8("\n")

  implicit def jsonAsyncStreamToResponse[A](implicit
    e: Encode.Json[A],
    w: Witness.Aux[Application.Json]
  ): Aux[AsyncStream[A], Application.Json] =
    asyncResponseBuilder((a, cs) => e(a, cs).concat(newLine))

  implicit def textAsyncStreamToResponse[A](implicit
    e: Encode.Text[A]
  ): Aux[AsyncStream[A], Text.Plain] =
    asyncResponseBuilder((a, cs) => e(a, cs).concat(newLine))
}

trait HighPriorityToResponseInstances extends LowPriorityToResponseInstances {

  private[this] def bufToResponse(buf: Buf, ct: String): Response = {
    val rep = Response()

    if (!buf.isEmpty) {
      rep.content = buf
      rep.contentType = ct
    }

    rep
  }

  implicit def valueToResponse[A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): Aux[A, CT] = instance((a, cs) => bufToResponse(e(a, cs), w.value))
}

object ToResponse extends HighPriorityToResponseInstances {

  implicit def cnilToResponse[CT <: String]: Aux[CNil, CT] =
    instance((_, _) => Response(Version.Http10, Status.NotFound))

  implicit def coproductToResponse[H, T <: Coproduct, CT <: String](implicit
    trH: ToResponse.Aux[H, CT],
    trT: ToResponse.Aux[T, CT]
  ): Aux[H :+: T, CT] = instance {
    case (Inl(h), cs) => trH(h, cs)
    case (Inr(t), cs) => trT(t, cs)
  }
}
