package io.finch

/**
 * This package contains an internal-use only type-classes and utilities that power Finch's API.
 *
 * It's not recommended to use any of the internal API directly, since it might change without any deprecation cycles.
 */
package object internal {

  @inline private[this] final val someTrue: Option[Boolean] = Some(true)
  @inline private[this] final val someFalse: Option[Boolean] = Some(false)

  /**
   * Enriches any string with fast `tooX` conversions.
   */
  implicit class TooFastString(val s: String) extends AnyVal {

    // copy-pasted from
    // https://github.com/sirthias/parboiled2/blob/master/parboiled-core/src/main/scala/org/parboiled2/CharUtils.scala
    private[this] def hexValue(c: Char): Int = (c & 0x1f) + ((c >> 6) * 0x19) - 0x10

    // adopted from Java's Long.parseLong
    // scalastyle:off return
    private[this] def parseLong(min: Long, max: Long): Option[Long] = {
      var negative = false
      var limit = -max
      var mulMin = limit / 10L
      var result = 0L

      var i = 0
      if (s.length > 0) {
        val firstChar = s.charAt(0)
        if (firstChar < '0') {
          if (firstChar == '-') {
            negative = true
            limit = min
            mulMin = min / 10L
          } else if (firstChar != '+') return None

          if (s.length == 1) return None

          i += 1
        }

        while (i < s.length) {
          val c = s.charAt(i)
          if ('0' <= c && c <= '9') {
            if (result < mulMin) return None
            result = result * 10L
            val digit = hexValue(c)
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
     * Converts this string to the optional boolean value.
     */
    def tooBoolean: Option[Boolean] = s match {
      case "true" => someTrue
      case "false" => someFalse
      case _ => None
    }

    /**
     * Converts this string to the optional integer value.
     */
    def tooInt: Option[Int] =
      if (s.length > 11) None else parseLong(Int.MinValue, Int.MaxValue).map(_.toInt)

    /**
     * Converts this string to the optional integer value.
     */
    def tooLong: Option[Long] =
      if (s.length > 20) None else parseLong(Long.MinValue, Long.MaxValue)
  }
}
