package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import java.nio.charset.Charset
import shapeless.{:+:, CNil, Coproduct, Witness}

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

  /**
    * Abstraction over [[Decode]] to select correct decoder according to Content-Type of a request
    */
  trait Dispatchable[A, CT] {
    def apply(contentType: Option[String], b: Buf, cs: Charset): Try[A]
  }

  object Dispatchable {

    def instance[A, CT](fn: (Option[String], Buf, Charset) => Try[A]):  Dispatchable[A, CT] = new Dispatchable[A, CT] {
      def apply(contentType: Option[String], b: Buf, cs: Charset): Try[A] = fn(contentType, b, cs)
    }

    implicit def mkCnil[A, CTH <: String](implicit decode: Decode.Aux[A, CTH]): Dispatchable[A, CTH :+: CNil] =
      instance((_, b, cs) => decode(b, cs))

    implicit def mkCons[A, CTH <: String, CTT <: Coproduct](implicit
      decode: Decode.Aux[A, CTH],
      witness: Witness.Aux[CTH],
      tail: Dispatchable[A, CTT]
    ): Dispatchable[A, CTH :+: CTT] = instance((contentType, b, cs) => {
      if (contentType.exists(ct => ct.equalsIgnoreCase(witness.value))) {
        decode(b, cs)
      } else {
        tail(contentType, b, cs)
      }
    })

    implicit def mkSingle[A, CT <: String](implicit
      decode: Decode.Aux[A, CT]
    ): Dispatchable[A, CT] = instance((_, b, cs) => decode(b, cs))

  }

}
