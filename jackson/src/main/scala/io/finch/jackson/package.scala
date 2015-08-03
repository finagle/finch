package io.finch

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8
import io.finch.request.DecodeAnyRequest
import io.finch.response.EncodeAnyResponse
import com.twitter.util.Try

import scala.reflect.ClassTag

package object jackson {

  implicit def decodeJackson(implicit mapper: ObjectMapper): DecodeAnyRequest =
    new DecodeAnyRequest {
      def apply[A: ClassTag](s: String): Try[A] = Try {
        val clazz = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]
        mapper.readValue[A](s, clazz)
      }
    }

  implicit def encodeJackson(implicit mapper: ObjectMapper): EncodeAnyResponse =
    new EncodeAnyResponse {
      def apply[A](rep: A): Buf = Utf8(mapper.writeValueAsString(rep))
      def contentType: String = "application/json"
    }
}
