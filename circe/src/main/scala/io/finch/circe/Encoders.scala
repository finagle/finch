package io.finch.circe

import java.nio.charset.Charset

import com.twitter.io.Buf
import io.circe._
import io.finch.Encode

trait Encoders {

  protected def print(json: Json, cs: Charset): Buf =
    Buf.ByteBuffer.Owned(Printer.noSpaces.printToByteBuffer(json, cs))

  /**
    * Maps Circe's [[Encoder]] to Finch's [[Encode]].
    */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.Json[A] =
    Encode.json((a, cs) => print(e(a), cs))
}
