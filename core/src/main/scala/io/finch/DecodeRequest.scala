package io.finch

import java.util.UUID

import com.twitter.util.{Return, Throw, Try}
import shapeless.{::, Generic, HNil}

/**
 * An abstraction that is responsible for decoding the request of type `A`.
 */
trait DecodeRequest[A] {
  def apply(s: String): Try[A]
}

trait LowPriorityDecodeRequestInstances {

  /**
   * Creates an instance for a given type.
   */
  def instance[A](f: String => Try[A]): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(s: String): Try[A] = f(s)
  }

  /**
   * Creates a [[DecodeRequest]] from [[shapeless.Generic]].
   *
   * Note: This is mostly a workaround for `RequestReader[String].as[CaseClassOfASingleString]`,
   *       because by some reason, compiler doesn't pick `ValueReaderOps` for `RequestReader[String]`.
   */
  implicit def decodeRequestFromGeneric[A](implicit
    gen: Generic.Aux[A, String :: HNil]
  ): DecodeRequest[A] = instance(s => Return(gen.from(s :: HNil)))
}

object DecodeRequest extends LowPriorityDecodeRequestInstances {

  /**
   * Returns an instance for a given type.
   */
  def apply[A](implicit dr: DecodeRequest[A]): DecodeRequest[A] = dr

  /**
   * A [[DecodeRequest]] instance for `String`.
   */
  implicit val decodeString: DecodeRequest[String] = instance(s => Return(s))

  /**
   * A [[DecodeRequest]] instance for `Int`.
   */
  implicit val decodeInt: DecodeRequest[Int] = instance(s => Try(s.toInt))

  /**
   * A [[DecodeRequest]] instance for `Long`.
   */
  implicit val decodeLong: DecodeRequest[Long] = instance(s => Try(s.toLong))

  /**
   * A [[DecodeRequest]] instance for `Float`.
   */
  implicit val decodeFloat: DecodeRequest[Float] = instance(s => Try(s.toFloat))

  /**
   * A [[DecodeRequest]] instance for `Double`.
   */
  implicit val decodeDouble: DecodeRequest[Double] = instance(s => Try(s.toDouble))

  /**
   * A [[DecodeRequest]] instance for `Boolean`.
   */
  implicit val decodeBoolean: DecodeRequest[Boolean] = instance(s => Try(s.toBoolean))

  /**
   * A [[DecodeRequest]] instance for `UUID`.
   */
  implicit val decodeUUID: DecodeRequest[UUID] = instance(s =>
    if (s.length != 36) Throw(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else Try(UUID.fromString(s))
  )
}
