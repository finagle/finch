package io.finch

import com.twitter.util.Try
import com.twitter.io.Buf
import spray.json._


package object sprayjson{
  implicit def decodeSpray[A](implicit format:JsonFormat[A]): Decode[A] = Decode.instance[A]({
    s =>Try(s.parseJson.convertTo[A])
  })

  implicit def encodeSpray[A](implicit format: JsonFormat[A]): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(a.toJson.prettyPrint))
}
