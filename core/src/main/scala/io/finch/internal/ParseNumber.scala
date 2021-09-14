package io.finch.internal

abstract class ParseNumber[@specialized(Int, Long) A] {

  protected def max: Long
  protected def min: Long
  protected def prepare(a: Long): A

  // adopted from Java's Long.parseLong
  // scalastyle:off return
  final def apply(s: String): Option[A] = {
    var negative = false
    var limit = -max
    var result = 0L

    var i = 0
    if s.length > 0 then {
      val firstChar = s.charAt(0)
      if firstChar < '0' then {
        if firstChar == '-' then {
          negative = true
          limit = min
        } else if firstChar != '+' then return None

        if s.length == 1 then return None

        i += 1
      }

      // skip zeros
      while i < s.length && s.charAt(i) == '0' do i += 1

      val mulMin = limit / 10L

      while i < s.length do {
        val c = s.charAt(i)
        if '0' <= c && c <= '9' then {
          if result < mulMin then return None
          result = result * 10L
          val digit = c - '0'
          if result < limit + digit then return None
          result = result - digit
        } else return None

        i += 1
      }
    } else return None

    Some(prepare(if negative then result else -result))
  }
  // scalastyle:on return
}

object parseInt extends ParseNumber[Int] {
  protected def min: Long = Int.MinValue
  protected def max: Long = Int.MaxValue
  protected def prepare(a: Long): Int = a.toInt
}

object parseLong extends ParseNumber[Long] {
  protected def min: Long = Long.MinValue
  protected def max: Long = Long.MaxValue
  protected def prepare(a: Long): Long = a
}
