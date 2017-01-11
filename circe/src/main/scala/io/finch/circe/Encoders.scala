package io.finch.circe

import com.twitter.io.Buf
import io.circe.{Encoder, Json}
import io.finch.Encode
import io.finch.internal.BufText
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

trait Encoders {

  protected def printString(json: Json): String
  protected def printBytes(json: Json): ByteBuffer

  /**
   * Maps Circe's [[Encoder]] to Finch's [[Encode]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.Json[A] = Encode.json {
    case (a, StandardCharsets.UTF_8) => Buf.ByteBuffer.Owned(printBytes(e(a)))
    case (a, cs) => BufText(printString(e(a)), cs)
  }

  implicit def encodeExceptionCirce[A <: Exception]: Encoder[A] = new Encoder[A] {
    override def apply(e: A): Json =
      Json.obj(("message", Option(e.getMessage).fold(Json.Null)(Json.fromString)))
  }
}
