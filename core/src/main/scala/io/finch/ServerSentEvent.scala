package io.finch

import cats.Show
import com.twitter.io.Buf
import java.nio.charset.{Charset, StandardCharsets}

case class ServerSentEvent[A](
  data: A,
  id: Option[String] = None,
  event: Option[String] = None,
  retry: Option[Long] = None
)

object ServerSentEvent {

  private def text(s: String, cs: Charset) = Buf.ByteArray.Owned(s.getBytes(cs.name))

  // scalastyle:off
  implicit def encodeEventStream[A](implicit
    A: Show[A]
  ): Encode.Aux[ServerSentEvent[A], Text.EventStream] = new Encode[ServerSentEvent[A]] {

    type ContentType = Text.EventStream

    private def newLine(cs: Charset): Buf = text("\n", cs)

    private def encodeData(a: ServerSentEvent[A], cs: Charset): Buf =
      text("data:", cs).concat(text(A.show(a.data), cs)).concat(newLine(cs))

    private def encodeEventType(a: ServerSentEvent[A], cs: Charset): Buf = a.event match {
      case Some(e) =>
        text("event:", cs).concat(text(e, cs)).concat(newLine(cs))
      case None =>
        Buf.Empty
    }

    private def encodeId(a: ServerSentEvent[A], cs: Charset): Buf = a.id match {
      case Some(i) =>
        text("id:", cs).concat(text(i, cs)).concat(newLine(cs))
      case None =>
        Buf.Empty
    }

    private def encodeRetry(a: ServerSentEvent[A], cs: Charset): Buf = a.retry match {
      case Some(r) =>
        text("retry:", cs).concat(text(r.toString, cs)).concat(newLine(cs))
      case None =>
        Buf.Empty
    }

    def apply(a: ServerSentEvent[A], cs: Charset): Buf = {
      encodeData(a, cs)
        .concat(encodeEventType(a, cs))
        .concat(encodeId(a, cs))
        .concat(encodeRetry(a, cs))
    }
  }
  // scalastyle:on
}

