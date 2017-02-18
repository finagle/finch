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

  // adopted from Java's Long.parseLong
  // scalastyle:off return
  private[this] def parseLong(s: String, min: Long, max: Long): Option[Long] = {
    var negative = false
    var limit = -max
    var result = 0L

    var i = 0
    if (s.length > 0) {
      val firstChar = s.charAt(0)
      if (firstChar < '0') {
        if (firstChar == '-') {
          negative = true
          limit = min
        } else if (firstChar != '+') return None

        if (s.length == 1) return None

        i += 1
      }

      // skip zeros
      while (i < s.length && s.charAt(i) == '0') i += 1

      val mulMin = limit / 10L

      while (i < s.length) {
        val c = s.charAt(i)
        if ('0' <= c && c <= '9') {
          if (result < mulMin) return None
          result = result * 10L
          val digit = c - '0'
          if (result < limit + digit) return None
          result = result - digit
        } else return None

        i += 1
      }
    } else return None

    Some(if (negative) result else -result)
  }
  // scalastyle:on return

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
      else parseLong(s, Int.MinValue, Int.MaxValue).map(_.toInt)

    /**
     * Converts this string to the optional integer value. The maximum allowed length for a number
     * string is 32.
     */
    final def tooLong: Option[Long] =
      if (s.length == 0 || s.length > 32) None
      else parseLong(s, Long.MinValue, Long.MaxValue)
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
