package io.finch

import _root_.argonaut.{CursorHistory, DecodeJson, EncodeJson, Json}
import com.twitter.util.{Return, Throw, Try}
import jawn.Parser
import jawn.support.argonaut.Parser.facade
import scala.util.{Failure, Success}

package object argonaut {

  /**
   * @param decode The argonaut ''DecodeJson'' to use for decoding
   * @tparam A The type of data that the ''DecodeJson'' will decode into
   * @return Create a Finch ''DecodeRequest'' from an argonaut ''DecodeJson''
   */

  implicit def decodeArgonaut[A](implicit decode: DecodeJson[A]): DecodeRequest[A] = DecodeRequest.instance[A] { s =>
    val err: (String, CursorHistory) => Try[A] = { (str, hist) => Throw[A](Error(str)) }
    Parser.parseFromString[Json](s)(facade) match {
      case Success(v) => decode.decodeJson(v).fold[Try[A]](err, Return(_))
      case Failure(error) => Throw[A](Error(error.getMessage))
    }
  }

  /**
   * @param encode The argonaut ''EncodeJson'' to use for decoding
   * @tparam A The type of data that the ''EncodeJson'' will encode use to create the json string
   * @return Create a Finch ''EncodeJson'' from an argonaut ''EncodeJson''
   */
  implicit def encodeArgonaut[A](implicit encode: EncodeJson[A]): EncodeResponse[A] =
    EncodeResponse.fromString[A]("application/json")(encode.encode(_).nospaces)
}
