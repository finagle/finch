package io.finch.circe

import com.twitter.io.Buf
import io.circe._
import io.finch.Encode
import java.nio.charset.Charset

trait Encoders {

  protected def print(json: Json, cs: Charset): Buf =
    Buf.ByteBuffer.Owned(Printer.noSpaces.prettyByteBuffer(json, cs))

  /**
   * Maps Circe's [[Encoder]] to Finch's [[Encode]].
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): Encode.Json[A] =
    Encode.json((a, cs) => print(e(a), cs))
}
