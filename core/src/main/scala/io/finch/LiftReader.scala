package io.finch

import com.twitter.io.{Buf, Reader}

/** Create stream `S[F, A]` from [[Reader]].
  */
trait LiftReader[S[_[_], _], F[_]] {

  final def apply(reader: Reader[Buf]): S[F, Buf] = apply(reader, identity)

  def apply[A](reader: Reader[Buf], process: Buf => A): S[F, A]
}
