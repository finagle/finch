/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.petstore

import io.finch.request._

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
