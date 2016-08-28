package io.finch.argonaut

import argonaut.{EncodeJson, PrettyParams}
import io.finch.Encode
import io.finch.internal.BufText

trait Encoders {

  protected def printer: PrettyParams

  /**
   * Maps Argonaut's [[EncodeJson]] to Finch's [[Encode]].
   */
  implicit def encodeArgonaut[A](implicit e: EncodeJson[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(printer.pretty(e.encode(a)), cs))
}
