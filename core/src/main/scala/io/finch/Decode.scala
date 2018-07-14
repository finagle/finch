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
  type ContentType

  def apply(b: Buf, cs: Charset): Try[A]
}

object Decode {

  type Aux[A, CT] = Decode[A] { type ContentType = CT }

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

trait AdaptableDecode[A, CT] {

  def apply(contentType: Option[String]): Decode.Aux[A, CT]

}

object AdaptableDecode {

  def instance[A, CT](fn: Option[String] => Decode.Aux[A, CT]):  AdaptableDecode[A, CT] = new AdaptableDecode[A, CT] {
    def apply(contentType: Option[String]): Decode.Aux[A, CT] = fn(contentType)
  }

  implicit def mkCnil[A, CTH <: String](implicit decode: Decode.Aux[A, CTH]): AdaptableDecode[A, CTH :+: CNil] =
    instance(_ => decode.asInstanceOf[Decode.Aux[A, CTH :+: CNil]])

  implicit def mkCons[A, CTH <: String, CTT <: Coproduct](implicit
    decode: Decode.Aux[A, CTH],
    witness: Witness.Aux[CTH],
    tail: AdaptableDecode[A, CTT]
  ): AdaptableDecode[A, CTH :+: CTT] = instance(contentType => {
    if (contentType.exists(ct => ct.equalsIgnoreCase(witness.value))) {
      decode.asInstanceOf[Decode.Aux[A, CTH :+: CTT]]
    } else {
      tail(contentType).asInstanceOf[Decode.Aux[A, CTH :+: CTT]]
    }
  })

  implicit def mkSingle[A, CT <: String](implicit
    decode: Decode.Aux[A, CT]
  ): AdaptableDecode[A, CT] = instance(_ => decode)

}
