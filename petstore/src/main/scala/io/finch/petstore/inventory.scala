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
