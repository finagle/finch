package io.finch.petstore

import io.finch._

/**
 * Represents a reader object that helps extract parameters from query params and bodies.
 */
object reader {
  /**
   *  Reads JSON representing a single Status from a given query parameter.
   */
  implicit val statusReader: RequestReader[Status] = param("status").map(Status.fromString)

  /**
   * Reads JSON representing a sequence of Status(es) from a given query parameter.
   */
  implicit val findByStatusReader: RequestReader[Seq[String]] = param("status").map { statuses =>
    statuses.split(",").map(_.trim)
  }

  /**
   *  Reads JSON representing Tag(s) from a given query parameter.
   */
  implicit val tagReader: RequestReader[Seq[String]] = param("tags").map { tags =>
    tags.split(",").map(_.trim)
  }

  /**
   * Reads JSON representing a Pet's name from a given query parameter.
   */
  implicit val nameReader: RequestReader[String] = param("name")
}
