package io.finch.circe

import io.circe.{Encoder, Json}
import io.finch.Encode
import io.finch.internal.BufText

trait Encoders {

  protected def print(json: Json): String

  /**
   * Maps Circe's [[Encoder]] to Finch's [[Encode]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(print(e(a)), cs))
}
