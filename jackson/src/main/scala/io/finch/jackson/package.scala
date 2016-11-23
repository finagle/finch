package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.util.Try
import io.finch.internal.BufText
import scala.reflect.ClassTag

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper, ct: ClassTag[A]
  ): Decode.Json[A] = Decode.json { (b, cs) =>
    val buf = ChannelBufferBuf.Owned.extract(b)
    if (buf.hasArray) Try(mapper.readValue(buf.array(), 0, buf.readableBytes(), ct.runtimeClass.asInstanceOf[Class[A]]))
    else Try(mapper.readValue(buf.toString(cs), ct.runtimeClass.asInstanceOf[Class[A]]))
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
