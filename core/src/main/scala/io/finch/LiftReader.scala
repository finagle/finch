package io.finch

import com.twitter.io.{Buf, Reader}
/**
 * Create stream `S[F, A]` from [[Reader]].
 */
trait LiftReader[F[_], S[_[_], _]] {
  def apply(r: Reader[Buf]): S[F, Buf]
}
