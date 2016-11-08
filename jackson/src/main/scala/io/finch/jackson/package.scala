package io.finch

import scala.reflect.ClassTag

import com.fasterxml.jackson.databind.ObjectMapper
import com.twitter.util.Try
import io.finch.internal.BufText

package object jackson {

  implicit def decodeJackson[A](implicit
    mapper: ObjectMapper, ct: ClassTag[A]
  ): Decode.Json[A] = Decode.json((b, cs) =>
    // TODO: Eliminate toString conversion
    // See https://github.com/finagle/finch/issues/511
    // Jackson can parse from byte[] automatically detecting the encoding.
    Try(mapper.readValue(BufText.extract(b, cs), ct.runtimeClass.asInstanceOf[Class[A]]))
  )

  implicit def encodeJackson[A](implicit mapper: ObjectMapper): Encode.Json[A] =
    Encode.json((a, cs) => BufText(mapper.writeValueAsString(a), cs))
}
