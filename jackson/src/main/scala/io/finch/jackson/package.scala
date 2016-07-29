package io.finch

import scala.reflect.ClassTag

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.util.Try
import io.finch.internal.BufText

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper, ct: ClassTag[A]
  ): Decode[A] = Decode.instance(s =>
    Try(mapper.readValue(s, ct.runtimeClass.asInstanceOf[Class[A]]))
  )

  implicit def encodeJackson[A](implicit mapper: ObjectMapper): Encode.Json[A] =
    Encode.json((a, cs) => BufText(mapper.writeValueAsString(a), cs))
}
