package io.finch

import java.nio.charset.Charset

import cats.Show
import com.twitter.io.Buf

case class ServerSentEvent[A](
    data: A,
    id: Option[String] = None,
    event: Option[String] = None,
    retry: Option[Long] = None
)

object ServerSentEvent {

  private def text(s: String, cs: Charset) = Buf.ByteArray.Owned(s.getBytes(cs.name))

  implicit def encodeEventStream[A](implicit
      A: Show[A]
  ): Encode.Aux[ServerSentEvent[A], Text.EventStream] = new Encode[ServerSentEvent[A]] {

    type ContentType = Text.EventStream

    def apply(sse: ServerSentEvent[A], cs: Charset): Buf = {
      val dataBuf = text("data:", cs).concat(text(A.show(sse.data), cs)).concat(text("\n", cs))
      val eventType = sse.event.map(e => s"event:$e\n").getOrElse("")
      val id = sse.id.map(id => s"id:$id\n").getOrElse("")
      val retry = sse.retry.map(retry => s"retry:$retry\n").getOrElse("")
      val restBuf = text(eventType + id + retry, cs)
      dataBuf.concat(restBuf)
    }
  }
}
