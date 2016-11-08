package io.finch

import com.twitter.util.Try
import io.finch.internal.BufText
import spray.json._

package object sprayjson{

  /**
    * @param format spray-json support for convert JSON val to specific type object
    * @tparam A the type of the data to decode into
    */
  implicit def decodeSpray[A](implicit format: JsonFormat[A]): Decode.Json[A] =
    // TODO: Eliminate toString conversion
    // See https://github.com/finagle/finch/issues/511
    // SprayJson can parse from byte[] if it represents a UTF-8 string.
    Decode.json((b, cs) => Try(BufText.extract(b, cs).parseJson.convertTo[A]))

  /**
    * @param format spray-json support for convert JSON val to specific type object
    * @tparam A the type of the data to decode from
    */
  implicit def encodeSpray[A](implicit format: JsonFormat[A]): Encode.Json[A] =
    Encode.json((a, cs) => BufText(a.toJson.prettyPrint, cs))
}
