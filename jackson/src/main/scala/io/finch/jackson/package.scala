package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.io.Buf
import com.twitter.util.Try
import io.finch.internal.{HttpContent, Utf32}
import java.nio.charset.StandardCharsets

package object jackson {

  // See https://github.com/FasterXML/jackson-module-scala/issues/187
  val objectMapper: ObjectMapper with ScalaObjectMapper =
     (new ObjectMapper with ScalaObjectMapper)
       .registerModule(DefaultScalaModule)
       .asInstanceOf[ObjectMapper with ScalaObjectMapper]

  private[this] val encodeJacksonInstance: Encode.Json[Any] = Encode.json {
    case (a, StandardCharsets.UTF_8) =>
      Buf.ByteArray.Owned(objectMapper.writeValueAsBytes(a))
    case (a, cs) =>
      Buf.ByteArray.Owned(objectMapper.writeValueAsString(a).getBytes(cs.name))
  }

  implicit def decodeJackson[A](implicit m: Manifest[A]): Decode.Json[A] = Decode.json {
    case (buf, StandardCharsets.UTF_8 | StandardCharsets.UTF_16 | Utf32) =>
      val (array, begin, end) = buf.asByteArrayWithBeginAndEnd
      Try(objectMapper.readValue[A](array, begin, end - begin))
    case (buf, cs) =>
      Try(objectMapper.readValue[A](buf.asString(cs)))
  }

  implicit def encodeJackson[A]: Encode.Json[A] = encodeJacksonInstance.asInstanceOf[Encode.Json[A]]
}
