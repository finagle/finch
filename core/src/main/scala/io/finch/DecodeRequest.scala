package io.finch

import com.twitter.util.{Throw, Try}
import java.util.UUID
import scala.reflect.ClassTag

/**
 * An abstraction that is responsible for decoding the request of type `A`.
 */
trait DecodeRequest[A] {
  def apply(s: String): Try[A]
}

object DecodeRequest {

  /**
   * Returns an instance for a given type.
   */
  def apply[A](implicit dr: DecodeRequest[A]): DecodeRequest[A] = dr

  /**
   * Creates an instance for a given type.
   */
  def instance[A](f: String => Try[A]): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(s: String): Try[A] = f(s)
  }

  /**
   * A [[DecodeRequest]] instance for `String`.
   */
  implicit val decodeString: DecodeRequest[String] = instance(s => Try(s))

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

  /**
   * Creates a [[DecodeRequest]] from [[DecodeAnyRequest ]].
   */
  implicit def decodeRequestFromAnyDecode[A](implicit
    d: DecodeAnyRequest,
    tag: ClassTag[A]
  ): DecodeRequest[A] = instance(s => d(s)(tag))
}

/**
 * An abstraction that is responsible for decoding the request of general type.
 */
trait DecodeAnyRequest {
  def apply[A: ClassTag](req: String): Try[A]
}
