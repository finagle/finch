package io.finch

import io.circe.{Decoder, Encoder}
import io.circe.jawn.decode
import com.twitter.util.{Try, Throw, Return}

package object circe {

  /**
   * @param d The [[io.circe.Decoder]] to use for decoding
   * @tparam A The type of data that the [[io.circe.Decoder]] will decode into
   * @return Create a Finch ''DecodeRequest'' from a [[io.circe.Decoder]]
   */
  implicit def decodeCirce[A](implicit d: Decoder[A]): DecodeRequest[A] =
    DecodeRequest(
      decode[A](_).fold[Try[A]](
        error => Throw[A](Error(error.getMessage)),
        Return(_)
      )
    )

  /**
   * @param e The [[io.circe.Encoder]] to use for decoding
   * @tparam A The type of data that the [[io.circe.Encoder]] will use to create the JSON string
   * @return Create a Finch ''EncodeJson'' from an [[io.circe.Encoder]]
   */
  implicit def encodeCirce[A](implicit e: Encoder[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(e(_).noSpaces)
}

