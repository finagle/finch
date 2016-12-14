package io.finch

import com.twitter.util.Try
import io.finch.internal.{BufText, HttpContent}
import java.nio.charset.StandardCharsets
import spray.json._

package object sprayjson {

  implicit def decodeSpray[A](implicit format: JsonReader[A]): Decode.Json[A] =
    Decode.json {
      case (buf, StandardCharsets.UTF_8) =>
        Try(JsonParser(buf.asByteArray).convertTo[A])
      case (buf, cs) => Try(BufText.extract(buf, cs).parseJson.convertTo[A])
    }

  implicit def encodeSpray[A](implicit format: JsonWriter[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(a.toJson.prettyPrint, cs))

  implicit def encodeExceptionSpray[A <: Exception]: JsonWriter[A] = new JsonWriter[A] {
    override def write(e: A): JsValue =
      JsObject("message" -> Option(e.getMessage).fold[JsValue](JsNull)(JsString.apply))
  }
}
