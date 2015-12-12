package io.finch

import io.circe.{Json, Printer}

package object circe extends Encoders with Decoders {

  override protected def print(json: Json): String = Printer.noSpaces.pretty(json)

  /**
   * Provides an implicit [[Printer]] that drops null keys.
   */
  object dropNullKeys extends Encoders with Decoders {

    private val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)

    override protected def print(json: Json): String = printer.pretty(json)
  }

  /**
   * Jackson Serializer
   */
  object jacksonSerializer extends Encoders with Decoders {

    override protected def print(json: Json): String = io.circe.jackson.jacksonPrint(json)
  }

}

