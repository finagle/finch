package io.finch.sse

import java.nio.charset.Charset

import cats.Show
import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import io.finch.{Encode, Text}
import io.finch.internal.{BufText, ToResponse}

case class ServerSentEvent[A](
  data: A,
  id: Option[String] = None,
  event: Option[String] = None,
  retry: Option[Long] = None
)

object ServerSentEvent {

  implicit def sseAsyncToResponse[A](implicit
     e: Encode.Aux[A, Text.EventStream]
  ): ToResponse.Aux[AsyncStream[A], Text.EventStream] =
    ToResponse.asyncResponseBuilder((a, cs) => e(a, cs).concat(ToResponse.NewLine))

  implicit def encodeEventStream[A](implicit s: Show[A]): Encode.Aux[ServerSentEvent[A], Text.EventStream] = {
    Encode.instance[ServerSentEvent[A], Text.EventStream]({ (event: ServerSentEvent[A], c: Charset) =>
      encodeEvent[A](event, c, { (a: A, cs: Charset) => BufText(s.show(a), cs) })
    })
  }

  private[sse] def encodeEvent[A](sse: ServerSentEvent[A], cs: Charset, e: (A, Charset) => Buf): Buf = {
    val dataBuf = BufText("data:", cs).concat(e(sse.data, cs)).concat(BufText("\n", cs))
    val eventType = sse.event.map(e => s"event:$e\n").getOrElse("")
    val id = sse.id.map(id => s"id:$id\n").getOrElse("")
    val retry = sse.retry.map(retry => s"retry:$retry\n").getOrElse("")
    val restBuf = BufText(eventType + id + retry, cs)
    dataBuf.concat(restBuf)
  }
}
