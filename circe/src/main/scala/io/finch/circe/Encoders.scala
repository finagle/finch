package io.finch.circe

import io.circe.{Json, Encoder, Printer}
import io.circe.jackson._
import io.finch.EncodeResponse

trait Encoders {

  protected def print(json: Json): String

  /**
   * Maps Circe's [[Encoder]] to Finch's [[EncodeResponse]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(a => print(e(a)))
}
