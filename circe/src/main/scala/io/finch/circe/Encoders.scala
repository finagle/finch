package io.finch.circe

import io.circe.{Encoder, Printer}
import io.finch.EncodeResponse

trait Encoders {

  protected def printer: Printer

  /**
   * Maps Circe's [[Encoder]] to Finch's [[EncodeResponse]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(a => printer.pretty(e(a)))
}
