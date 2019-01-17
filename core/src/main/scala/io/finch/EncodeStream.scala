package io.finch

import com.twitter.io.{Buf, Reader}
import java.nio.charset.Charset

/**
 * A type-class that defines encoding of a stream in a shape of `S[F[_], A]` to Finagle's [[Reader]].
 */
trait EncodeStream[F[_], S[_[_], _], A] {

  type ContentType <: String

  def apply(s: S[F, A], cs: Charset): F[Reader[Buf]]
}

object EncodeStream {

  type Aux[F[_], S[_[_], _], A, CT <: String] =
    EncodeStream[F, S, A] { type ContentType = CT }

  type Json[F[_], S[_[_],_], A] = Aux[F, S, A, Application.Json]

  type Text[F[_], S[_[_],_], A] = Aux[F, S, A, Text.Plain]
}


