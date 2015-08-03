package io.finch

import _root_.argonaut.{EncodeJson, Json, Parse, DecodeJson}
import io.finch.request.DecodeRequest
import io.finch.request.RequestError
import io.finch.response.EncodeResponse
import com.twitter.util.{Try, Throw, Return}

package object argonaut {

  /**
   * @param decode The argonaut ''DecodeJson'' to use for decoding
   * @tparam A The type of data that the ''DecodeJson'' will decode into
   * @return Create a Finch ''DecodeRequest'' from an argonaut ''DecodeJson''
   */
  implicit def decodeArgonaut[A](implicit decode: DecodeJson[A]): DecodeRequest[A] =
    DecodeRequest(
      Parse.decodeEither(_).fold[Try[A]](
        error => Throw[A](new RequestError(error)),
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
