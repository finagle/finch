package io.finch

import com.twitter.util.{Return, Throw, Try}
import java.util.UUID
import shapeless._

/**
 * Decodes an HTTP entity (eg: header, query-string param) represented as UTF-8 `String` into
 * an arbitrary type `A`.
 */
trait DecodeEntity[A] {
  def apply(s: String): Try[A]
}

object DecodeEntity {

  /**
   * Returns a [[DecodeEntity]] instance for a given type.
   */
  @inline def apply[A](implicit d: DecodeEntity[A]): DecodeEntity[A] = d

  /**
   * Creates an [[DecodeEntity]] instance from a given function `String => Try[A]`.
   */
  def instance[A](fn: String => Try[A]): DecodeEntity[A] = new DecodeEntity[A] {
    def apply(s: String): Try[A] = fn(s)
  }

  /**
   * Creates a [[Decode]] from [[shapeless.Generic]].
   *
   * Note: This is mostly a workaround for `Endpoint[String].as[CaseClassOfASingleString]`,
   *       because by some reason, compiler doesn't pick `ValueEndpointOps` for
   *       `Endpoint[String]`.
   */
  implicit def decodeFromGeneric[A](implicit
    gen: Generic.Aux[A, String :: HNil]
  ): DecodeEntity[A] = instance(s => Return(gen.from(s :: HNil)))

  implicit val decodeString: DecodeEntity[String] = instance(s => Return(s))

  implicit val decodeInt: DecodeEntity[Int] = instance(s => Try(s.toInt))

  implicit val decodeLong: DecodeEntity[Long] = instance(s => Try(s.toLong))

  implicit val decodeFloat: DecodeEntity[Float] = instance(s => Try(s.toFloat))

  implicit val decodeDouble: DecodeEntity[Double] = instance(s => Try(s.toDouble))

  implicit val decodeBoolean: DecodeEntity[Boolean] = instance(s => Try(s.toBoolean))

  implicit val decodeUUID: DecodeEntity[UUID] = instance(s =>
    if (s.length != 36) Throw(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else Try(UUID.fromString(s))
  )
}
