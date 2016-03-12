package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.Serialization._

package object json4s {

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to decode into
   */
  implicit def decodeJson[A : Manifest](implicit formats: Formats): Decode[A] =
    Decode.instance(input => Try(JsonMethods.parse(input).extract[A]))

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to encode
   * @return
   */
  implicit def encodeJson[A <: AnyRef](implicit formats: Formats): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(write(a)))
}
