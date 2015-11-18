package io.finch.argonaut

import argonaut.{EncodeJson, PrettyParams}
import io.finch.EncodeResponse

trait Encoders {

  protected def printer: PrettyParams

  /**
   * Maps Argonaut's [[EncodeJson]] to Finch's [[EncodeResponse]].
   */
  implicit def encodeArgonaut[A](implicit e: EncodeJson[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(a => printer.pretty(e.encode(a)))
}
