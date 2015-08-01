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

import _root_.argonaut._
import argonaut.Argonaut._

/**
 * Represents Pets in the Petstore. Each Pet has a unique ID that should not be known by
 * the user at the time of its creation.
 * @param id The pet's auto-generated, unique ID.
 * @param name (Required) The pet's name.
 * @param photoUrls (Required) A sequence of URLs that lead to uploaded photos of the pet.
 * @param category The type of pet (cat, dragon, fish, etc.)
 * @param tags Tags that describe this pet.
 * @param status (Available, Pending, or Adopted)
 */
case class Pet(
    id: Option[Long],
    name: String,
    photoUrls: Seq[String],
    category: Option[Category],
    tags: Option[Seq[Tag]],
    status: Option[Status] //available, pending, adopted
    )

/**
 * Provides a codec for decoding and encoding Pet objects.
 */
object Pet {
  implicit val petCodec: CodecJson[Pet] = //instance of a type class
    casecodec6(Pet.apply, Pet.unapply)("id", "name", "photoUrls", "category", "tags", "status")
}
