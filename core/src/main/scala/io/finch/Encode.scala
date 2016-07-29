package io.finch

import cats.Show
import cats.data.Xor
import com.twitter.io.Buf
import io.finch.internal.BufText
import java.nio.charset.Charset
import shapeless.Witness

/**
 * An abstraction that is responsible for encoding the value of type `A` into a `Buf` with a given
 * charset.
 */
trait Encode[A] {
  type ContentType <: String

  def apply(a: A, cs: Charset): Buf
}

trait LowPriorityEncodeInstances {

  type Aux[A, CT <: String] = Encode[A] { type ContentType = CT }

  type Json[A] = Aux[A, Application.Json]
  type Text[A] = Aux[A, Text.Plain]

  def instance[A, CT <: String](fn: (A, Charset) => Buf): Aux[A, CT] =
    new Encode[A] {
      type ContentType = CT
      def apply(a: A, cs: Charset): Buf = fn(a, cs)
    }

  def json[A](fn: (A, Charset) => Buf): Json[A] =
    instance[A, Application.Json](fn)

  def text[A](fn: (A, Charset) => Buf): Text[A] =
    instance[A, Text.Plain](fn)

  implicit def encodeShow[A](implicit s: Show[A]): Text[A] =
    text((a, cs) => BufText(s.show(a), cs))
}

object Encode extends LowPriorityEncodeInstances {

  class Implicitly[A] {
    def apply[CT <: String](w: Witness.Aux[CT])(implicit
      e: Encode.Aux[A, CT]
    ): Encode.Aux[A, CT] = e
  }

  @inline def apply[A]: Implicitly[A] = new Implicitly[A]

  implicit def encodeUnit[CT <: String]: Aux[Unit, CT] =
    instance((_, _) => Buf.Empty)

  implicit def encodeBuf[CT <: String]: Aux[Buf, CT] =
    instance((buf, _) => buf)

  implicit val encodeExceptionAsTextPlain: Text[Exception] = text(
    (e, cs) => BufText(Option(e.getMessage).getOrElse(""), cs)
  )

  implicit val encodeExceptionAsJson: Json[Exception] = json(
    (e, cs) => BufText(s"""{"message":"${Option(e.getMessage).getOrElse("")}"}""", cs)
  )

  implicit val encodeString: Text[String] =
    text((s, cs) => BufText(s, cs))

  implicit def encodeXor[A, B, CT <: String](implicit
    ae: Encode.Aux[A, CT],
    be: Encode.Aux[B, CT]
  ): Encode.Aux[A Xor B, CT] = instance[A Xor B, CT](
    (xor, cs) => xor.fold(a => ae(a, cs), b => be(b, cs))
  )
}
