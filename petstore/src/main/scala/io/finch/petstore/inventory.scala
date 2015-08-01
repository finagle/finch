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

import argonaut.CodecJson
import argonaut.Argonaut._

/**
 * Represents the current state of the Petstore and how many pets are currently of which [[Status]].
 */
case class Inventory(available: Int, pending: Int, adopted: Int)

/**
 * Provides a codec for encoding and decoding [[Inventory]] objects.
 */
object Inventory {
  implicit val inventoryCodec: CodecJson[Inventory] =
    CodecJson(
      (i: Inventory) =>
        ("available" := i.available) ->: ("pending" := i.pending) ->: ("adopted" := i.adopted) ->: jEmptyObject,
      c => for {
        available <- (c --\ "available").as[Int]
        pending <- (c --\ "pending").as[Int]
        adopted <- (c --\ "adopted").as[Int]
      } yield Inventory(available, pending, adopted))
}
