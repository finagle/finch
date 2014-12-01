package io.finch.json

package object finch {
  implicit object EncodeFinchJson extends EncodeJson[Json] {
    def apply(json: Json): String = Json.encode(json)
  }

  implicit object DecodeFinchJson extends DecodeJson[Json] {
    def apply(json: String): Option[Json] = Json.decode(json)
  }
}
