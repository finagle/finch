package io.finch.circe

import com.twitter.io.Buf
import io.circe.{Encoder, Json}
import io.finch.Encode

trait Encoders {

  protected def print(json: Json): String

  /**
   * Maps Circe's [[Encoder]] to Finch's [[Encode]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(print(e(a))))
}
