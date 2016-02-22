package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import play.api.libs.json._

package object playjson {

  /**
   * @param reads Play JSON `Reads` to use for decoding
   * @tparam A the type of the data to decode into
   */
  implicit def decodePlayJson[A](implicit reads: Reads[A]): Decode[A] =
    Decode.instance(input => Try(Json.parse(input).as[A]))

  /**
   * @param writes Play JSON `Writes` to use for encoding
   * @tparam A the type of the data to encode from
   */
  implicit def encodePlayJson[A](implicit writes: Writes[A]): Encode.ApplicationJson[A] =
    Encode.applicationJson(a => Buf.Utf8(Json.stringify(Json.toJson(a))))
}
