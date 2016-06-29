package io.finch.internal

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

  def apply(a: A): Response
}

trait LowPriorityToResponseInstances {
  type Aux[A, CT] = ToResponse[A] { type ContentType = CT }

  def instance[A, CT <: String](fn: A => Response): Aux[A, CT] = new ToResponse[A] {
    type ContentType = CT
    def apply(a: A): Response = fn(a)
  }

  implicit def responseToResponse[CT <: String]: Aux[Response, CT] = instance(identity)

  private[this] def asyncStreamResponseBuilder[A, CT <: String](writer: A => Buf)(implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[A], CT] = instance { as =>
    val rep = Response()
    rep.setChunked(true)

    val writable = rep.writer

    as.foreachF(chunk => writable.write(writer(chunk))).ensure(writable.close())
    rep.contentType = w.value

    rep
  }

  implicit def asyncBufToResponse[CT <: String](implicit
    w: Witness.Aux[CT]
  ): Aux[AsyncStream[Buf], CT] = asyncStreamResponseBuilder(identity)

  private[this] val newLine: Buf = Buf.Utf8("\n")

  implicit def jsonAsyncStreamToResponse[A](implicit
    e: Encode.ApplicationJson[A],
    w: Witness.Aux[Application.Json]
  ): Aux[AsyncStream[A], Application.Json] = asyncStreamResponseBuilder(a => e(a).concat(newLine))

  implicit def textAsyncStreamToResponse[A](implicit
    e: Encode.TextPlain[A]
  ): Aux[AsyncStream[A], Text.Plain] = asyncStreamResponseBuilder(a => e(a).concat(newLine))
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
  ): Aux[A, CT] = instance(a => bufToResponse(e(a), w.value))

  implicit def outputToResponse[A, CT <: String](implicit
    tr: ToResponse.Aux[A, CT],
    e: Encode.Aux[Exception, CT],
    w: Witness.Aux[CT]
  ): Aux[Output[A], CT] = instance { o =>
    val rep = o match {
      case Output.Payload(v, _) => tr(v)
      case Output.Failure(x, _) => bufToResponse(e(x), w.value)
      case Output.Empty(_) => Response()
    }
    rep.status = o.status
    o.headers.foreach { case (k, v) => rep.headerMap.set(k, v) }
    o.cookies.foreach(rep.cookies.add)

    rep
  }
}

object ToResponse extends HighPriorityToResponseInstances {

  implicit def cnilToResponse[CT <: String]: Aux[CNil, CT] =
    instance(_ => Response(Version.Http10, Status.NotFound))

  implicit def coproductToResponse[H, T <: Coproduct, CT <: String](implicit
    trH: ToResponse.Aux[H, CT],
    trT: ToResponse.Aux[T, CT]
  ): Aux[H :+: T, CT] = instance {
    case Inl(h) => trH(h)
    case Inr(t) => trT(t)
  }
}
