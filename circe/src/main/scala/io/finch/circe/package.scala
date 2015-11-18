package io.finch

import io.circe.Printer

package object circe extends Encoders with Decoders {

  override protected val printer: Printer = Printer.noSpaces

  /**
   * Provides an implicit [[Printer]] that drops null keys.
   */
  object dropNullKeys extends Encoders with Decoders {
    override protected val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)
  }
}

