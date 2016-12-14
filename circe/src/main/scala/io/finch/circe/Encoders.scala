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

  implicit def encodeExceptionCirce[A <: Exception]: Encoder[A] = new Encoder[A] {
    override def apply(e: A): Json =
      Json.obj(("message", Option(e.getMessage).fold(Json.Null)(Json.fromString)))
  }
}
