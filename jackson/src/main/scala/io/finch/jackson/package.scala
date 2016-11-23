package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.util.Try
import io.finch.internal.{BufText, HttpContent, Utf32}
import java.nio.charset.StandardCharsets
import scala.reflect.ClassTag

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper, ct: ClassTag[A]
  ): Decode.Json[A] = Decode.json {
    case (buf, StandardCharsets.UTF_8 | StandardCharsets.UTF_16 | Utf32) =>
      val (array, offset, length) = buf.asByteArrayWithOffsetAndLength
      Try(mapper.readValue(array, offset, length, ct.runtimeClass.asInstanceOf[Class[A]]))
    case (buf, cs) =>
      Try(mapper.readValue(BufText.extract(buf, cs), ct.runtimeClass.asInstanceOf[Class[A]]))
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
