package io.finch

import scala.reflect.ClassTag

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.util.Try

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper, ct: ClassTag[A]
  ): DecodeRequest[A] = DecodeRequest.instance(s =>
    Try(mapper.readValue(s, implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]))
  )

  implicit def encodeJackson[A](implicit mapper: ObjectMapper): EncodeResponse[A] =
    EncodeResponse.fromString("application/json")(a => mapper.writeValueAsString(a))
}
