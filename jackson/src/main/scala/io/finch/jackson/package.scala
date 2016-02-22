package io.finch

import scala.reflect.ClassTag

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.io.Buf
import com.twitter.util.Try

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper, ct: ClassTag[A]
  ): Decode[A] = Decode.instance(s =>
    Try(mapper.readValue(s, ct.runtimeClass.asInstanceOf[Class[A]]))
  )

  implicit def encodeJackson[A](implicit mapper: ObjectMapper): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(mapper.writeValueAsString(a)))
}
