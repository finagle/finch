package io.finch.internal

import java.nio.charset.{Charset, StandardCharsets}

import com.twitter.io.Buf

object newLine {

  private def fromCharset(cs: Charset): Buf = Buf.ByteArray.Owned("\n".getBytes(cs))

  private val ascii = fromCharset(StandardCharsets.US_ASCII)
  private val utf16be = fromCharset(StandardCharsets.UTF_16BE)
  private val utf16le = fromCharset(StandardCharsets.UTF_16LE)
  private val utf16 = fromCharset(StandardCharsets.UTF_16)
  private val utf32 = fromCharset(Utf32)

  final def apply(cs: Charset): Buf = cs match {
    case StandardCharsets.UTF_8      => ascii
    case StandardCharsets.US_ASCII   => ascii
    case StandardCharsets.ISO_8859_1 => ascii
    case StandardCharsets.UTF_16     => utf16
    case StandardCharsets.UTF_16BE   => utf16be
    case StandardCharsets.UTF_16LE   => utf16le
    case Utf32                       => utf32
    case _                           => fromCharset(cs)
  }
}
