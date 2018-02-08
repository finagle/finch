package io.finch

import com.twitter.io.Buf
import io.circe.{Json, Printer}
import java.nio.charset.{Charset, StandardCharsets}

package object circe extends Encoders with Decoders {

  /**
   * Provides a [[Printer]] that drops null keys.
   */
  object dropNullValues extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
    override protected def print(json: Json, cs: Charset): Buf =
      Buf.ByteBuffer.Owned(printer.prettyByteBuffer(json, cs))
  }

  /**
   * Provides Jackson Serializer.
   */
  @deprecated("Use standard Circe serializer - it works great!", "0.17")
  object jacksonSerializer extends Encoders with Decoders {
    override protected def print(json: Json, cs: Charset): Buf =
      if (cs == StandardCharsets.UTF_8)
        Buf.ByteBuffer.Owned(io.circe.jackson.jacksonPrintByteBuffer(json))
      else
        Buf.ByteArray.Owned(io.circe.jackson.jacksonPrint(json).getBytes(cs.name))
  }

  /**
   * Provides a [[Printer]] that uses a simple form of feedback-controller to predict the
   * size of the printed message.
   */
  object predictSize extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(predictSize = true)
    override protected def print(json: Json, cs: Charset): Buf =
      Buf.ByteBuffer.Owned(printer.prettyByteBuffer(json, cs))
  }

  object accumulating extends Encoders with AccumulatingDecoders
}
