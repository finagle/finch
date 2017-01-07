package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.io.Buf
import com.twitter.util.Try
import io.finch.internal.{BufText, HttpContent, Utf32}
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
      BufText(objectMapper.writeValueAsString(a), cs)
  }

  implicit def decodeJackson[A](implicit m: Manifest[A]): Decode.Json[A] = Decode.json {
    case (buf, StandardCharsets.UTF_8 | StandardCharsets.UTF_16 | Utf32) =>
      val (array, offset, length) = buf.asByteArrayWithOffsetAndLength
      Try(objectMapper.readValue[A](array, offset, length))
    case (buf, cs) =>
      Try(objectMapper.readValue[A](BufText.extract(buf, cs)))
  }

  implicit def encodeJackson[A]: Encode.Json[A] = encodeJacksonInstance.asInstanceOf[Encode.Json[A]]

  implicit def encodeExceptionJackson[A <: Exception]: Encode.Json[A] =
    Encode.json((a, cs) => {
      val rootNode = objectMapper.createObjectNode()
      rootNode.put("message", a.getMessage)
      BufText(objectMapper.writeValueAsString(rootNode), cs)
    })
}
