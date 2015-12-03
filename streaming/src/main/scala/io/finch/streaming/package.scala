package io.finch

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Status, Response}
import com.twitter.finagle.http.Version.Http11
import com.twitter.io.{Reader, Buf}
import io.finch.internal.ToResponse

package object streaming {

  implicit def asyncRequestReader[A](rr: RequestReader[A]): AsyncReaderOps[A] = new AsyncReaderOps[A](rr)

  /**
    * Provides implicit ToResponse for `AsyncStream[Option[Buf]]` since vanilla [[Endpoint]] is synchronous
    * by it's nature
    * If current element of stream is None sends EOF to client and closes connection otherwise sends data to client
    *
    * Http server should be initialized with `.withStreaming(enabled=true)`
    */
  implicit def asyncToResponse: ToResponse[AsyncStream[Option[Buf]]] = new ToResponse[AsyncStream[Option[Buf]]] {
    override def apply(a: AsyncStream[Option[Buf]]): Response = {
      val writable = Reader.writable()
      a.foreachF {
        case Some(buf) => writable.write(buf)
        case None => writable.close()
      }
      Response(Http11, Status.Ok, writable)
    }
  }
}
