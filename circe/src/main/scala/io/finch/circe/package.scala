package io.finch

import io.circe.{Json, Printer}

package object circe extends Encoders with Decoders {

  override protected def printString(json: Json): String = Printer.noSpaces.pretty(json)

  /**
   * Provides a [[Printer]] that drops null keys.
   */
  object dropNullKeys extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)
    override protected def printString(json: Json): String = printer.pretty(json)
  }

  /**
   * Provides Jackson Serializer.
   */
  object jacksonSerializer extends Encoders with Decoders {
    override protected def printString(json: Json): String =
      io.circe.jackson.jacksonPrint(json)
  }
}

