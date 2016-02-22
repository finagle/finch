package io.finch

import java.util.UUID

import com.twitter.util.{Return, Throw, Try}
import shapeless.{::, Generic, HNil}

/**
 * An abstraction that is responsible for decoding the value of type `A`.
 */
trait Decode[A] {
  def apply(s: String): Try[A]
}

trait LowPriorityDecodeInstances {

  /**
   * Creates an instance for a given type.
   */
  def instance[A](f: String => Try[A]): Decode[A] = new Decode[A] {
    def apply(s: String): Try[A] = f(s)
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
  ): Decode[A] = instance(s => Return(gen.from(s :: HNil)))
}

object Decode extends LowPriorityDecodeInstances {

  /**
   * Returns an instance for a given type.
   */
  @inline def apply[A](implicit dr: Decode[A]): Decode[A] = dr

  /**
   * A [[Decode]] instance for `String`.
   */
  implicit val decodeString: Decode[String] = instance(s => Return(s))

  /**
   * A [[Decode]] instance for `Int`.
   */
  implicit val decodeInt: Decode[Int] = instance(s => Try(s.toInt))

  /**
   * A [[Decode]] instance for `Long`.
   */
  implicit val decodeLong: Decode[Long] = instance(s => Try(s.toLong))

  /**
   * A [[Decode]] instance for `Float`.
   */
  implicit val decodeFloat: Decode[Float] = instance(s => Try(s.toFloat))

  /**
   * A [[Decode]] instance for `Double`.
   */
  implicit val decodeDouble: Decode[Double] = instance(s => Try(s.toDouble))

  /**
   * A [[Decode]] instance for `Boolean`.
   */
  implicit val decodeBoolean: Decode[Boolean] = instance(s => Try(s.toBoolean))

  /**
   * A [[Decode]] instance for `UUID`.
   */
  implicit val decodeUUID: Decode[UUID] = instance(s =>
    if (s.length != 36) Throw(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else Try(UUID.fromString(s))
  )
}
