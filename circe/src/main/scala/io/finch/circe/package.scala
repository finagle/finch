package io.finch

import io.circe.{Json, Printer}
import java.nio.ByteBuffer

package object circe extends Encoders with Decoders {

  override protected def printString(json: Json): String = Printer.noSpaces.pretty(json)
  override protected def printBytes(json: Json): ByteBuffer = Printer.noSpaces.prettyByteBuffer(json)

  /**
   * Provides a [[Printer]] that drops null keys.
   */
  object dropNullKeys extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)
    override protected def printString(json: Json): String = printer.pretty(json)
    override protected def printBytes(json: Json): ByteBuffer = printer.prettyByteBuffer(json)
  }

  /**
   * Provides Jackson Serializer.
   */
  object jacksonSerializer extends Encoders with Decoders {
    override protected def printString(json: Json): String =
      io.circe.jackson.jacksonPrint(json)
    override protected def printBytes(json: Json): ByteBuffer =
      io.circe.jackson.jacksonPrintByteBuffer(json)
  }
}

