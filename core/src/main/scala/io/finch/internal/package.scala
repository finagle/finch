package io.finch

import com.twitter.finagle.http.Message
import com.twitter.io.Buf
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

/**
 * This package contains an internal-use only type-classes and utilities that power Finch's API.
 *
 * It's not recommended to use any of the internal API directly, since it might change without any
 * deprecation cycles.
 */
package object internal {

  @inline private[this] final val someTrue: Option[Boolean] = Some(true)
  @inline private[this] final val someFalse: Option[Boolean] = Some(false)

  // Missing in StandardCharsets.
  val Utf32: Charset = Charset.forName("UTF-32")

  /**
   * Enriches any string with fast `tooX` conversions.
   */
  implicit class TooFastString(val s: String) extends AnyVal {

    /**
     * Converts this string to the optional boolean value.
     */
    final def tooBoolean: Option[Boolean] = s match {
      case "true" => someTrue
      case "false" => someFalse
      case _ => None
    }

    /**
     * Converts this string to the optional integer value. The maximum allowed length for a number
     * string is 32.
     */
    final def tooInt: Option[Int] =
      if (s.length == 0 || s.length > 32) None
      else parseInt(s)

    /**
     * Converts this string to the optional long value. The maximum allowed length for a number
     * string is 32.
     */
    final def tooLong: Option[Long] =
      if (s.length == 0 || s.length > 32) None
      else parseLong(s)
  }

  implicit class HttpMessage(val self: Message) extends AnyVal {
    // Returns message's charset or UTF-8 if it's not defined.
    def charsetOrUtf8: Charset = self.charset match {
      case Some(cs) => Charset.forName(cs)
      case None => StandardCharsets.UTF_8
    }
  }

  implicit class HttpContent(val self: Buf) extends AnyVal {
    // Returns content as ByteArray (tries to avoid copying).
    def asByteArrayWithBeginAndEnd: (Array[Byte], Int, Int) = {
      // Finagle guarantees to have the payload on heap when it enters the
      // user land. With a cost of a tuple allocation we're making this agnostic
      // to the underlying Netty version.
      val Buf.ByteArray.Owned(array, begin, end) = Buf.ByteArray.coerce(self)
      (array, begin, end)
    }

    // Returns content as ByteBuffer (tries to avoid copying).
    def asByteBuffer: ByteBuffer = {
      val (array, begin, end) = asByteArrayWithBeginAndEnd
      ByteBuffer.wrap(array, begin, end - begin)
    }

    // Returns content as ByteArray (tries to avoid copying).
    def asByteArray: Array[Byte] = asByteArrayWithBeginAndEnd match {
      case (array, begin, end) if begin == 0 && end == array.length => array
      case (array, begin, end) =>
        val result = new Array[Byte](end - begin)
        System.arraycopy(array, begin, result, 0, end - begin)

        result
    }

    // Returns content as String (tries to avoid copying).
    def asString(cs: Charset): String = {
      val (array, begin, end) = asByteArrayWithBeginAndEnd
      new String(array, begin, end - begin, cs.name)
    }
  }
}
