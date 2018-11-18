package io.finch.streaming

import java.nio.charset.Charset

import com.twitter.io.Buf
import io.finch.Application
import scala.annotation.implicitNotFound

/**
  * Stream HTTP streamed payload represented as S[F, Buf] into
  * a S[F, A] of arbitrary type `A`.
  */
trait StreamDecode[S[_[_], _], F[_], A] {

  type ContentType <: String

  def apply(stream: S[F, Buf], cs: Charset): S[F, A]

}

object StreamDecode {

  @implicitNotFound(
"""An Enumerator endpoint requires implicit Enumerate instance in scope, probably decoder for ${A} is missing.

  Make sure ${A} is one of the following:

  * A com.twitter.io.Buf
  * A value of a type with an io.finch.iteratee.Enumerate instance (with the corresponding content-type)
"""
  )
  type Aux[S[_[_], _], F[_], A, CT <: String] = StreamDecode[S, F, A] {type ContentType = CT}

  type Json[S[_[_], _], F[_], A] = Aux[S, F, A, Application.Json]

  def instance[S[_[_], _], F[_], A, CT <: String]
  (f: (S[F, Buf], Charset) => S[F, A]): StreamDecode.Aux[S, F, A, CT] = {
    new StreamDecode[S, F, A] {
      type ContentType = CT

      def apply(stream: S[F, Buf], cs: Charset): S[F, A] = f(stream, cs)

    }
  }

  implicit def buf2bufStreamDecoder[S[_[_], _], F[_], CT <: String]: Aux[S, F, Buf, CT] =
    instance((stream, _) => stream)
}
