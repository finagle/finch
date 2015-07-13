package io.finch.petstore

import io.finch.argonaut._
import io.finch.request._

/**
 * Represents a reader object that helps extract parameters from query params and bodies.
 */
object reader {
  /**
   *  Reads JSON representing a Status from a given query parameter.
   */
  implicit val statusReader: RequestReader[Status] = param("status").map(Status.fromString)

  /**
   *  Reads JSON representing a Tag from a given query parameter.
   */
  implicit val tagReader: RequestReader[Seq[String]] = param("tags").map { tags =>
    tags.split(",").map(_.trim)
  }

  /**
   * Reads JSON representing a Pet's name from a given query parameter.
   */
  implicit val nameReader: RequestReader[String] = param("name")
}
