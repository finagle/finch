package io.finch.request

import java.util.UUID

import com.twitter.util.{Throw, Try}
import scala.reflect.ClassTag

/**
 * An abstraction that is responsible for decoding the request of type `A`.
 */
trait DecodeRequest[A] {
  def apply(req: String): Try[A]
}

object DecodeRequest {
  /**
   * Convenience method for creating new [[io.finch.request.DecodeRequest DecodeRequest]] instances.
   */
  def apply[A](f: String => Try[A]): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(value: String): Try[A] = f(value)
  }

  /**
   * A [[DecodeRequest]] instance for `String`.
   */
  implicit val decodeString: DecodeRequest[String] = DecodeRequest { s => Try(s) }

  /**
   * A [[DecodeRequest]] instance for `Int`.
   */
  implicit val decodeInt: DecodeRequest[Int] = DecodeRequest { s => Try(s.toInt) }

  /**
   * A [[DecodeRequest]] instance for `Long`.
   */
  implicit val decodeLong: DecodeRequest[Long] = DecodeRequest { s => Try(s.toLong) }

  /**
   * A [[DecodeRequest]] instance for `Float`.
   */
  implicit val decodeFloat: DecodeRequest[Float] = DecodeRequest { s => Try(s.toFloat) }

  /**
   * A [[DecodeRequest]] instance for `Double`.
   */
  implicit val decodeDouble: DecodeRequest[Double] = DecodeRequest { s => Try(s.toDouble) }

  /**
   * A [[DecodeRequest]] instance for `Boolean`.
   */
  implicit val decodeBoolean: DecodeRequest[Boolean] = DecodeRequest { s => Try(s.toBoolean) }

  /**
   * A [[DecodeRequest]] instance for `UUID`.
   */
  implicit val decodeUUID: DecodeRequest[UUID] = DecodeRequest { s =>
    if (s.length != 36) Throw(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else Try(UUID.fromString(s))
  }

  /**
   * Creates a [[DecodeRequest]] from [[DecodeAnyRequest ]].
   */
  implicit def decodeRequestFromAnyDecode[A](implicit
    d: DecodeAnyRequest,
    tag: ClassTag[A]
  ): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(req: String): Try[A] = d(req)(tag)
  }
}

/**
 * An abstraction that is responsible for decoding the request of general type.
 */
trait DecodeAnyRequest {
  def apply[A: ClassTag](req: String): Try[A]
}
