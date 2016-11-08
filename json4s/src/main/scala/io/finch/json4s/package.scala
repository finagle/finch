package io.finch

import com.twitter.util.Try
import io.finch.internal.BufText
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.Serialization._

package object json4s {

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to decode into
   */
  implicit def decodeJson[A : Manifest](implicit formats: Formats): Decode.Json[A] =
    Decode.json((b, cs) => Try(JsonMethods.parse(BufText.extract(b, cs)).extract[A]))

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to encode
   * @return
   */
  implicit def encodeJson[A <: AnyRef](implicit formats: Formats): Encode.Json[A] =
    Encode.json((a, cs) => BufText(write(a), cs))
}
