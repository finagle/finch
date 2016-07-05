package io.finch

import cats.Show
import cats.data.Xor
import com.twitter.io.Buf
import shapeless.Witness

/**
 * An abstraction that is responsible for encoding the value of type `A`.
 */
trait Encode[A] {
  type ContentType <: String

  def apply(a: A): Buf
}

trait LowPriorityEncodeInstances {

  type Aux[A, CT <: String] = Encode[A] { type ContentType = CT }

  type ApplicationJson[A] = Aux[A, Application.Json]
  type TextPlain[A] = Aux[A, Text.Plain]

  def instance[A, CT <: String](fn: A => Buf): Aux[A, CT] =
    new Encode[A] {
      type ContentType = CT
      def apply(a: A): Buf = fn(a)
    }

  def json[A](fn: A => Buf): ApplicationJson[A] =
    instance[A, Witness.`"application/json"`.T](fn)

  def text[A](fn: A => Buf): TextPlain[A] =
    instance[A, Witness.`"text/plain"`.T](fn)

  implicit def encodeShow[A](implicit s: Show[A]): TextPlain[A] =
    text(a => Buf.Utf8(s.show(a)))

  implicit def encodeExceptionAsTextPlain[EE <: Exception]: TextPlain[EE] =
    text(e => Buf.Utf8(Option(e.getMessage).getOrElse("")))

  implicit def encodeExceptionAsJson[EE <: Exception]: ApplicationJson[EE] =
    json(e => Buf.Utf8(s"""{"message": "${Option(e.getMessage).getOrElse("")}"}"""))
}

object Encode extends LowPriorityEncodeInstances {

  class Implicitly[A] {
    def apply[CT <: String](w: Witness.Aux[CT])(implicit
      e: Encode.Aux[A, CT]
    ): Encode.Aux[A, CT] = e
  }

  @inline def apply[A]: Implicitly[A] = new Implicitly[A]

  implicit def encodeUnit[CT <: String]: Aux[Unit, CT] =
    instance(_ => Buf.Empty)

  implicit def encodeBuf[CT <: String]: Aux[Buf, CT] =
    instance(identity)

  implicit val encodeString: TextPlain[String] =
    text(Buf.Utf8.apply)

  implicit def encodeXor[A, B, CT <: String](implicit
    ae: Encode.Aux[A, CT],
    be: Encode.Aux[B, CT]
  ): Encode.Aux[A Xor B, CT] = instance[A Xor B, CT](xor => xor.fold(ae.apply, be.apply))
}
