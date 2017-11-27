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

object DecodeEntity extends LowPriorityDecode {

  /**
   * Returns a [[DecodeEntity]] instance for a given type.
   */
  @inline def apply[A](implicit d: DecodeEntity[A]): DecodeEntity[A] = d

  implicit val decodeString: DecodeEntity[String] = instance(s => Return(s))

}

trait LowPriorityDecode extends LowLowPriorityDecode {

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

trait LowLowPriorityDecode {

  /**
    * Creates an [[DecodeEntity]] instance from a given function `String => Try[A]`.
    */
  def instance[A](fn: String => Try[A]): DecodeEntity[A] = new DecodeEntity[A] {
    def apply(s: String): Try[A] = fn(s)
  }

  /**
    * Creates a [[Decode]] from [[shapeless.Generic]] for single value case classes.
    */
  implicit def decodeFromGeneric[A, H <: HList, E](implicit
    gen: Generic.Aux[A, H],
    ev: (E :: HNil) =:= H,
    de: DecodeEntity[E]
  ): DecodeEntity[A] = instance(s => de(s).map(b => gen.from(b :: HNil)))
}
