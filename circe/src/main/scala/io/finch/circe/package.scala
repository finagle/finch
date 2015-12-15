package io.finch

import io.circe.{Json, Printer}
import io.circe.jackson._

package object circe extends Encoders with Decoders {

  override protected def print(json: Json): String = Printer.noSpaces.pretty(json)

  /**
   * Provides a [[Printer]] that drops null keys.
   */
  object dropNullKeys extends Encoders with Decoders {
    private val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)
    override protected def print(json: Json): String = printer.pretty(json)
  }

  /**
   * Provides Jackson Serializer.
   */
  object jacksonSerializer extends Encoders with Decoders {
    override protected def print(json: Json): String = jacksonPrint(json)
  }
}

