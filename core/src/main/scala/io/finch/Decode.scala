package io.finch

import java.nio.charset.Charset

import scala.util.control.NoStackTrace

import com.twitter.io.Buf
import shapeless.{:+:, CNil, Coproduct, Witness}

/**
  * Decodes an HTTP payload represented as [[Buf]] (encoded with [[Charset]]) into
  * an arbitrary type `A`.
  */
trait Decode[A] {
  type ContentType <: String

  def apply(b: Buf, cs: Charset): Either[Throwable, A]
}

object Decode {

  /**
    * Indicates that a payload can not be decoded with a given [[Decode]] instance (or a coproduct
    * of instances).
    */
  object UnsupportedMediaTypeException extends Exception with NoStackTrace

  type Aux[A, CT <: String] = Decode[A] { type ContentType = CT }

  type Json[A] = Aux[A, Application.Json]
  type Text[A] = Aux[A, Text.Plain]

  /**
    * Creates an instance for a given type.
    */
  def instance[A, CT <: String](fn: (Buf, Charset) => Either[Throwable, A]): Aux[A, CT] = new Decode[A] {
    type ContentType = CT
    def apply(b: Buf, cs: Charset): Either[Throwable, A] = fn(b, cs)
  }

  def json[A](fn: (Buf, Charset) => Either[Throwable, A]): Json[A] =
    instance[A, Application.Json](fn)

  def text[A](fn: (Buf, Charset) => Either[Throwable, A]): Text[A] =
    instance[A, Text.Plain](fn)

  /**
    * Returns a [[Decode]] instance for a given type (with required content type).
    */
  @inline def apply[A, CT <: String](implicit d: Aux[A, CT]): Aux[A, CT] = d

  /**
    * Abstracting over [[Decode]] to select a correct decoder according to the `Content-Type` header
    * value.
    */
  trait Dispatchable[A, CT] {
    def apply(ct: String, b: Buf, cs: Charset): Either[Throwable, A]
  }

  object Dispatchable {

    implicit def cnilToDispatchable[A]: Dispatchable[A, CNil] = new Dispatchable[A, CNil] {
      def apply(ct: String, b: Buf, cs: Charset): Either[Throwable, A] =
        Left(Decode.UnsupportedMediaTypeException)
    }

    implicit def coproductToDispatchable[A, CTH <: String, CTT <: Coproduct](implicit
        decode: Decode.Aux[A, CTH],
        witness: Witness.Aux[CTH],
        tail: Dispatchable[A, CTT]
    ): Dispatchable[A, CTH :+: CTT] = new Dispatchable[A, CTH :+: CTT] {
      def apply(ct: String, b: Buf, cs: Charset): Either[Throwable, A] =
        if (ct.equalsIgnoreCase(witness.value)) decode(b, cs)
        else tail(ct, b, cs)
    }

    implicit def singleToDispatchable[A, CT <: String](implicit
        decode: Decode.Aux[A, CT],
        witness: Witness.Aux[CT]
    ): Dispatchable[A, CT] = coproductToDispatchable[A, CT, CNil].asInstanceOf[Dispatchable[A, CT]]
  }
}
