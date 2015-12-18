package io.finch.streaming

import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Reader, Buf}
import io.finch.RequestReader

class AsyncReaderOps[A](val rr: RequestReader[A]) extends AnyVal {

  /**
    * Asynchronously read bytes from the request.
    * `None` element represents the end of stream.
    *
    * @param chunkSize size of chunk to read
    */
  def async(chunkSize: Int): RequestReader[AsyncStream[Option[Buf]]] = {
    RequestReader(request => read(chunkSize, request.reader))
  }

  /**
    * Asynchronously read bytes from the request with default chunk size equals to `Int.MaxValue`
    * `None` element represents the end of stream.
    */
  def async(): RequestReader[AsyncStream[Option[Buf]]] = async(Int.MaxValue)

  private def read(chunkSize: Int, reader: Reader): AsyncStream[Option[Buf]] =
    AsyncStream.fromFuture(reader.read(chunkSize)).flatMap {
      case Some(buf) => Some(buf) +:: read(chunkSize, reader)
      case None => AsyncStream.empty
    }

}
