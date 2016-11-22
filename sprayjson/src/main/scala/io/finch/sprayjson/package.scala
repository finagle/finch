package io.finch

import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.util.Try
import io.finch.internal.{extractBytesFromArrayBackedBuf, BufText}
import java.nio.charset.StandardCharsets
import spray.json._

package object sprayjson {

  /**
    * @param format spray-json support for convert JSON val to specific type object
    * @tparam A the type of the data to decode into
    */
  implicit def decodeSpray[A](implicit format: JsonReader[A]): Decode.Json[A] =
    Decode.json { (b, cs) =>
      cs match {
        case StandardCharsets.UTF_8 =>
          val buf = ChannelBufferBuf.Owned.extract(b)
          if (buf.hasArray) Try(JsonParser(extractBytesFromArrayBackedBuf(buf)).convertTo[A])
          else Try(JsonParser(buf.toString(cs)).convertTo[A])
        case _ => Try(BufText.extract(b, cs).parseJson.convertTo[A])
      }
    }

  /**
    * @param format spray-json support for convert JSON val to specific type object
    * @tparam A the type of the data to decode from
    */
  implicit def encodeSpray[A](implicit format: JsonWriter[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(a.toJson.prettyPrint, cs))

  implicit def encodeExceptionSpray[A <: Exception]: JsonWriter[A] = new JsonWriter[A] {
    override def write(e: A): JsValue = JsObject("message" -> JsString(e.getMessage))
  }
}
