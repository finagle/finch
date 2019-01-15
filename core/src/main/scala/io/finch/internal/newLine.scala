package io.finch.internal

import com.twitter.io.Buf
import java.nio.charset.{Charset, StandardCharsets}

object newLine {

  private def create(cs: Charset): Buf = Buf.ByteArray.Owned("\n".getBytes(cs))

  private val ascii = create(StandardCharsets.US_ASCII)
  private val utf16be = create(StandardCharsets.UTF_16BE)
  private val utf16le = create(StandardCharsets.UTF_16LE)
  private val utf16 = create(StandardCharsets.UTF_16)
  private val utf32 = create(Utf32)

  final def apply(cs: Charset): Buf = cs match {
    case StandardCharsets.UTF_8 => ascii
    case StandardCharsets.US_ASCII => ascii
    case StandardCharsets.ISO_8859_1 => ascii
    case StandardCharsets.UTF_16 => utf16
    case StandardCharsets.UTF_16BE => utf16be
    case StandardCharsets.UTF_16LE => utf16le
    case Utf32 => utf32
    case _ => create(cs)
  }
}
