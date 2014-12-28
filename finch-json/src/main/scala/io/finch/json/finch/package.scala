package io.finch.json

import io.finch.request.DecodeJson
import io.finch.response.EncodeJson

package object finch {
  implicit val encodeFinchJson = new EncodeJson[Json] {
    def apply(json: Json): String = Json.encode(json)
  }

  implicit val decodeFinchJson = new DecodeJson[Json] {
    def apply(json: String): Option[Json] = Json.decode(json)
  }
}
