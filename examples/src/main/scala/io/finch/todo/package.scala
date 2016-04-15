package io.finch

import io.circe.{Encoder, Json}

package object todo {
  implicit val encodeException: Encoder[Exception] = Encoder.instance(e =>
    Json.obj(
      "type" -> Json.fromString(e.getClass.getSimpleName),
      "message" -> Json.fromString(e.getMessage)
    )
  )
}
