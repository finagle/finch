package io.finch

import java.nio.charset.Charset

import scala.annotation.implicitNotFound

import com.twitter.io.Buf

/**
  * Stream HTTP streamed payload represented as S[F, Buf] into
  * a S[F, A] of arbitrary type `A`.
  */
trait DecodeStream[S[_[_], _], F[_], A] {

  type ContentType <: String

  def apply(stream: S[F, Buf], cs: Charset): S[F, A]

}

object DecodeStream {

  @implicitNotFound(
    """A stream* endpoint requires implicit DecodeStream instance in scope, probably streaming decoder for ${A} is missing.

  Make sure ${A} is one of the following:

  * A com.twitter.io.Buf
  * A value of a type with an io.finch.DecodeStream instance (with the corresponding content-type)

  Help: If you're looking for JSON stream decoding, consider to use decoder from finch-circe library
"""
  )
  type Aux[S[_[_], _], F[_], A, CT <: String] = DecodeStream[S, F, A] { type ContentType = CT }

  type Json[S[_[_], _], F[_], A] = Aux[S, F, A, Application.Json]

  def instance[S[_[_], _], F[_], A, CT <: String](f: (S[F, Buf], Charset) => S[F, A]): DecodeStream.Aux[S, F, A, CT] =
    new DecodeStream[S, F, A] {
      type ContentType = CT

      def apply(stream: S[F, Buf], cs: Charset): S[F, A] = f(stream, cs)

    }

}
