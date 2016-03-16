package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import spray.json._

package object sprayjson{

  /**
    * @param format spray-json support for convert JSON val to specific type object
    * @tparam A the type of the data to decode into
    */
  implicit def decodeSpray[A](implicit format: JsonFormat[A]): Decode.ApplicationJson[String, A] = {
    Decode.applicationJson(
      s =>Try(s.parseJson.convertTo[A])
    )
  }

  /**
    * @param format spray-json support for convert JSON val to specific type object
    * @tparam A the type of the data to decode from
    */
  implicit def encodeSpray[A](implicit format: JsonFormat[A]): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(a.toJson.prettyPrint))
}
