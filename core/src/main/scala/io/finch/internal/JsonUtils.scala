package io.finch.internal

import scala.annotation.switch

/**
  * Utility to escape JSON strings
  *
  * This code is borrowed from Circe JSON Library
  */
private[finch] object JsonUtils {
  private[this] def escapeChar(c: Char): String = (c: @switch) match {
    case '\\' => "\\\\"
    case '"' => "\\\""
    case '\b' => "\\b"
    case '\f' => "\\f"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case possibleUnicode => if (Character.isISOControl(possibleUnicode)) {
      "\\u%04x".format(possibleUnicode.toInt)
    } else possibleUnicode.toString
  }

  private[this] def isNormalChar(c: Char): Boolean = (c: @switch) match {
    case '\\' => false
    case '"' => false
    case '\b' => false
    case '\f' => false
    case '\n' => false
    case '\r' => false
    case '\t' => false
    case possibleUnicode => !Character.isISOControl(possibleUnicode)
  }

  def escape(s: String): String = s.foldLeft("")((s, c) =>
    s + (if (isNormalChar(c)) c else escapeChar(c))
  )
}
