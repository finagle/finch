package io.finch

import com.twitter.util.Try
import io.finch.internal.BufText
import play.api.libs.json._

package object playjson {

  /**
   * @param reads Play JSON `Reads` to use for decoding
   * @tparam A the type of the data to decode into
   */
  implicit def decodePlayJson[A](implicit reads: Reads[A]): Decode.Json[A] =
    // TODO: Eliminate toString conversion
    // See https://github.com/finagle/finch/issues/511
    // PlayJson can parse from byte[] automatically detecting the charset.
    Decode.json((b, cs) => Try(Json.parse(BufText.extract(b, cs)).as[A]))

  /**
   * @param writes Play JSON `Writes` to use for encoding
   * @tparam A the type of the data to encode from
   */
  implicit def encodePlayJson[A](implicit writes: Writes[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(Json.stringify(Json.toJson(a)), cs))
}
