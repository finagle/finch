package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.util.Try
import io.finch.internal.{BufText, HttpContent, Utf32}
import java.nio.charset.StandardCharsets

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper with ScalaObjectMapper, m: Manifest[A]
  ): Decode.Json[A] = Decode.json {
    case (buf, StandardCharsets.UTF_8 | StandardCharsets.UTF_16 | Utf32) =>
      val (array, offset, length) = buf.asByteArrayWithOffsetAndLength
      Try(mapper.readValue[A](array, offset, length))
    case (buf, cs) =>
      Try(mapper.readValue[A](BufText.extract(buf, cs)))
  }

  implicit def encodeJackson[A](implicit mapper: ObjectMapper): Encode.Json[A] =
    Encode.json((a, cs) => BufText(mapper.writeValueAsString(a), cs))

  implicit def encodeExceptionJackson[A <: Exception](implicit mapper: ObjectMapper): Encode.Json[A] =
    Encode.json((a, cs) => {
      val rootNode = mapper.createObjectNode()
      rootNode.put("message", a.getMessage)
      BufText(mapper.writeValueAsString(rootNode), cs)
    })
}
