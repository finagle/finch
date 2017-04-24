package io.finch.circe

import com.twitter.io.Buf
import io.circe.{Encoder, Json}
import io.finch.Encode

trait Encoders {

  protected def printString(json: Json): String

  /**
   * Maps Circe's [[Encoder]] to Finch's [[Encode]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.Json[A] = Encode.json {
    case (a, cs) => Buf.ByteArray.Owned(printString(e(a)).getBytes(cs.name))
  }

  implicit val encodeExceptionCirce: Encoder[Exception] = Encoder.instance(e =>
    Json.obj("message" -> Option(e.getMessage).fold(Json.Null)(Json.fromString))
  )
}
