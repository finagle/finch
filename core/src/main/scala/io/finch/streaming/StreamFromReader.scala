package io.finch.streaming

import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Buf, Reader}

/**
  * Create stream S[F, Buf] from com.twitter.io.Reader[Buf]
  */
trait StreamFromReader[S[_[_], _], F[_]] {

  def apply(reader: Reader[Buf]): S[F, Buf]

}

object StreamFromReader {

  def instance[S[_[_], _], F[_]](fn: Reader[Buf] => S[F, Buf]): StreamFromReader[S, F] = new StreamFromReader[S, F] {
    def apply(reader: Reader[Buf]): S[F, Buf] = fn(reader)
  }

  type AsyncStreamF[F[_], A] = AsyncStream[A]

  implicit def asyncStreamBufReader[F[_]]: StreamFromReader[AsyncStreamF, F] = instance[AsyncStreamF, F](reader =>
    Reader.toAsyncStream(reader)
  )

}
