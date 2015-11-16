package io.finch

import _root_.argonaut.{DecodeJson, EncodeJson, Parse}
import com.twitter.util.{Return, Throw, Try}

package object argonaut {

  /**
   * @param decode The argonaut ''DecodeJson'' to use for decoding
   * @tparam A The type of data that the ''DecodeJson'' will decode into
   * @return Create a Finch ''DecodeRequest'' from an argonaut ''DecodeJson''
   */
  implicit def decodeArgonaut[A](implicit decode: DecodeJson[A]): DecodeRequest[A] = DecodeRequest.instance(s =>
    Parse.decodeEither(s).fold[Try[A]](
      error => Throw[A](Error(error)),
      Return(_)
    )
  )

  /**
   * @param encode The argonaut ''EncodeJson'' to use for decoding
   * @tparam A The type of data that the ''EncodeJson'' will encode use to create the json string
   * @return Create a Finch ''EncodeJson'' from an argonaut ''EncodeJson''
   */
  implicit def encodeArgonaut[A](implicit encode: EncodeJson[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(encode.encode(_).nospaces)
}
