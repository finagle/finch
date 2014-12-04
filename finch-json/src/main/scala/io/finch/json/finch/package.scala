package io.finch.json

package object finch {
  implicit val encodeFinchJson = new EncodeJson[Json] {
    def apply(json: Json): String = Json.encode(json)
  }

  implicit val decodeFinchJson = new DecodeJson[Json] {
    def apply(json: String): Option[Json] = Json.decode(json)
  }
}
