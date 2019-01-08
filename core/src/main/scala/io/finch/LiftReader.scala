package io.finch

import com.twitter.io.{Buf, Reader}

/**
  * Create stream S[F, Buf] from com.twitter.io.Reader[Buf]
  */
trait LiftReader[S[_[_], _], F[_]] {

  def apply(reader: Reader[Buf]): S[F, Buf]

}

object LiftReader {

  def instance[S[_[_], _], F[_]](fn: Reader[Buf] => S[F, Buf]): LiftReader[S, F] = new LiftReader[S, F] {
    def apply(reader: Reader[Buf]): S[F, Buf] = fn(reader)
  }

}
