package io.finch.petstore

import io.finch.argonaut._
import io.finch.request._

object reader {
  implicit val statusReader: RequestReader[Status] = param("status").map(Status.fromString)

  implicit val tagReader: RequestReader[Seq[String]] = param("tags").map { tags =>
    tags.split(",").map(_.trim)
  }

  implicit val nameReader: RequestReader[String] = param("name")
}
