package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import java.nio.charset.Charset

/**
 * Decodes an HTTP payload represented as [[Buf]] (encoded with [[Charset]]) into
 * an arbitrary type `A`.
 */
trait Decode[A] {
  type ContentType <: String

  def apply(b: Buf, cs: Charset): Try[A]
}

object Decode {

  type Aux[A, CT <: String] = Decode[A] { type ContentType = CT }

  type Json[A] = Aux[A, Application.Json]
  type Text[A] = Aux[A, Text.Plain]

  /**
   * Creates an instance for a given type.
   */
  def instance[A, CT <: String](fn: (Buf, Charset) => Try[A]): Aux[A, CT] = new Decode[A] {
    type ContentType = CT
    def apply(b: Buf, cs: Charset): Try[A] = fn(b, cs)
  }

  def json[A](fn: (Buf, Charset) => Try[A]): Json[A] =
    instance[A, Application.Json](fn)

  def text[A](fn: (Buf, Charset) => Try[A]): Text[A] =
    instance[A, Text.Plain](fn)

  /**
   * Returns a [[Decode]] instance for a given type (with required content type).
   */
  @inline def apply[A, CT <: String](implicit d: Aux[A, CT]): Aux[A, CT] = d
}
