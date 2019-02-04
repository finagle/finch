package io.finch

import java.nio.charset.Charset
import scala.annotation.implicitNotFound

import com.twitter.io.Buf

/**
  * Stream HTTP streamed payload represented as S[F, Buf] into
  * a S[F, A] of arbitrary type `A`.
  */
trait DecodeStream[F[_], S[_[_], _], A] {

  type ContentType <: String

  def apply(s: S[F, Buf], cs: Charset): S[F, A]
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
  type Aux[F[_], S[_[_], _], A, CT <: String] = DecodeStream[F, S, A] { type ContentType = CT }

  type Json[F[_], S[_[_], _], A] = Aux[F, S, A, Application.Json]
}
