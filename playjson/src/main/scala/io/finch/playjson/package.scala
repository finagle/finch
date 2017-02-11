package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import io.finch.internal.{HttpContent, Utf32}
import java.nio.charset.StandardCharsets
import play.api.libs.json._

package object playjson {

  /**
   * @param reads Play JSON `Reads` to use for decoding
   * @tparam A the type of the data to decode into
   */
  implicit def decodePlayJson[A](implicit reads: Reads[A]): Decode.Json[A] =
    Decode.json {
      case (buf, StandardCharsets.UTF_8 | StandardCharsets.UTF_16 | Utf32) =>
        Try(Json.parse(buf.asByteArray).as[A])
      case (buf, cs) =>
        Try(Json.parse(buf.asString(cs)).as[A])
    }

  /**
   * @param writes Play JSON `Writes` to use for encoding
   * @tparam A the type of the data to encode from
   */
  implicit def encodePlayJson[A](implicit writes: Writes[A]): Encode.Json[A] =
    Encode.json((a, cs) => Buf.ByteArray.Owned(Json.stringify(Json.toJson(a)).getBytes(cs.name)))

  implicit val encodeExceptionPlayJson: Writes[Exception] = new Writes[Exception] {
    override def writes(e: Exception): JsValue = Json.obj("message" -> e.getMessage)
  }
}
