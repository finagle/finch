package io.finch

import java.util.UUID

import com.twitter.util.{Return, Throw, Try}
import shapeless.{::, Generic, HNil, Witness}

/**
 * An abstraction that is responsible for decoding the value of type `A`.
 */
trait Decode[I, A] {
  type ContentType <: String
  def apply(i: I): Try[A]
}

trait LowPriorityDecodeInstances {

  type Aux[I, A, CT <: String] = Decode[I, A] { type ContentType = CT }

  type ApplicationJson[I, A] = Aux[I, A, Witness.`"application/json"`.T]
  type TextPlain[I, A] = Aux[I, A, Witness.`"text/plain"`.T]

  /**
   * Creates an instance for a given type.
   */
  def instance[I, A, CT <: String](f: I => Try[A]): Aux[I, A, CT] = new Decode[I, A] {
    type ContentType = CT
    def apply(i: I): Try[A] = f(i)
  }

  def applicationJson[I, A](fn: I => Try[A]): ApplicationJson[I, A] =
    instance[I, A, Witness.`"application/json"`.T](fn)

  def textPlain[I, A](fn: I => Try[A]): TextPlain[I, A] =
    instance[I, A, Witness.`"text/plain"`.T](fn)

  /**
   * Creates a [[Decode]] from [[shapeless.Generic]].
   *
   * Note: This is mostly a workaround for `Endpoint[String].as[CaseClassOfASingleString]`,
   *       because by some reason, compiler doesn't pick `ValueEndpointOps` for
   *       `Endpoint[String]`.
   */
  implicit def decodeFromGeneric[A, CT <: String](implicit
    gen: Generic.Aux[A, String :: HNil],
    w: Witness.Aux[CT]
  ): Decode.Aux[String, A, CT] = instance(s => Return(gen.from(s :: HNil)))
}

object Decode extends LowPriorityDecodeInstances {

  class Implicitly[I, A] {
    def apply[CT <: String](w: Witness.Aux[CT])(implicit
      d: Decode.Aux[I, A, CT]
    ): Decode.Aux[I, A, CT] = d
  }

  @inline def apply[I, A]: Implicitly[I, A] = new Implicitly[I, A]

  /**
   * A [[Decode]] instance for `String`.
   */
  implicit val decodeString: TextPlain[String, String] = textPlain(s => Return(s))

  /**
   * A [[Decode]] instance for `Int`.
   */
  implicit val decodeInt: Decode[String, Int] = textPlain(s => Try(s.toInt))

  /**
   * A [[Decode]] instance for `Long`.
   */
  implicit val decodeLong: Decode[String, Long] = textPlain(s => Try(s.toLong))

  /**
   * A [[Decode]] instance for `Float`.
   */
  implicit val decodeFloat: Decode[String, Float] = textPlain(s => Try(s.toFloat))

  /**
   * A [[Decode]] instance for `Double`.
   */
  implicit val decodeDouble: Decode[String, Double] = textPlain(s => Try(s.toDouble))

  /**
   * A [[Decode]] instance for `Boolean`.
   */
  implicit val decodeBoolean: Decode[String, Boolean] = textPlain(s => Try(s.toBoolean))

  /**
   * A [[Decode]] instance for `UUID`.
   */
  implicit val decodeUUID: Decode[String, UUID] = textPlain(s =>
    if (s.length != 36) Throw(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else Try(UUID.fromString(s))
  )
}
