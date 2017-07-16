package io.finch

import com.twitter.io.Buf
import io.circe.{Json, Printer}
import java.nio.charset.{Charset, StandardCharsets}

package object circe extends Encoders with Decoders {

  protected def print(json: Json, cs: Charset): Buf =
    Buf.ByteBuffer.Owned(Printer.noSpaces.prettyByteBuffer(json, cs))

  /**
   * Provides a [[Printer]] that drops null keys.
   */
  object dropNullKeys extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)
    protected def print(json: Json, cs: Charset): Buf =
      Buf.ByteBuffer.Owned(printer.prettyByteBuffer(json, cs))
  }

  /**
   * Provides Jackson Serializer.
   */
  object jacksonSerializer extends Encoders with Decoders {
    protected def print(json: Json, cs: Charset): Buf =
      if (cs == StandardCharsets.UTF_8)
        Buf.ByteBuffer.Owned(io.circe.jackson.jacksonPrintByteBuffer(json))
      else
        Buf.ByteArray.Owned(io.circe.jackson.jacksonPrint(json).getBytes(cs.name))
  }
}
