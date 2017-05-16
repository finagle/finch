package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import io.finch.internal.HttpContent
import java.nio.charset.StandardCharsets
import spray.json._

package object sprayjson {

  implicit def decodeSpray[A](implicit format: JsonReader[A]): Decode.Json[A] =
    Decode.json {
      case (buf, StandardCharsets.UTF_8) =>
        Try(JsonParser(buf.asByteArray).convertTo[A])
      case (buf, cs) => Try(buf.asString(cs).parseJson.convertTo[A])
    }

  implicit def encodeSpray[A](implicit format: JsonWriter[A]): Encode.Json[A] =
    Encode.json((a, cs) => Buf.ByteArray.Owned(a.toJson.compactPrint.getBytes(cs.name)))
}
