package io.finch

import com.twitter.io.{Buf, Reader}
import java.nio.charset.Charset

/**
 * A type-class that defines encoding of a stream in a shape of `S[F[_], A]` to Finagle's [[Reader]].
 */
trait EncodeStream[S[_[_], _], F[_], A] {

  type ContentType <: String

  def apply(s: S[F, A], cs: Charset): Reader[Buf]
}

object EncodeStream {

  type Aux[S[_[_], _], F[_], A, CT <: String] =
    EncodeStream[S, F, A] { type ContentType = CT }

  type Json[S[_[_],_], F[_], A] = Aux[S, F, A, Application.Json]

  type Text[S[_[_],_], F[_], A] = Aux[S, F, A, Text.Plain]
}


