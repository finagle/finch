package io.finch.petstore

import argonaut.{CodecJson, DecodeResult, DecodeJson, EncodeJson}

/**
 * Represents the status of a particular order for pets. Can be "placed," "approved," or "delivered."
 */
sealed trait OrderStatus {
  /**
   * @return The string representation of the OrderStatus.
   */
  def code: String
}

/**
 * The status of an order after it has been placed.
 */
case object Placed extends OrderStatus {
  /**
   * @return The string representation of the OrderStatus: "placed."
   */
  def code: String = "placed"
}

/**
 * The status of an order after it has been approved by the store.
 */
case object Approved extends OrderStatus {
  /**
   * @return The string representation of the OrderStatus: "approved."
   */
  def code: String = "approved"
}

/**
 * The status of an order after it has been delivered and completed.
 */
case object Delivered extends OrderStatus {
  /**
   * @return The string representation of the OrderStatus: "delivered."
   */
  def code: String = "delivered"
}

/**
 * Provides encode and decode methods for OrderStatus objects.
 * If asked to decode a string other than "placed," "approved," or "delivered" the
 * system will fail.
 */
object OrderStatus {
  /**
   * Coverts a given string into its corresponding OrderStatus object.
   * @return OrderStatus object corresponding to s.
   */
  def fromString(s: String): OrderStatus = s match {
    case "placed" => Placed
    case "approved" => Approved
    case "delivered" => Delivered
  }

  /**
   * Encodes a given OrderStatus into JSON.
   */
  val orderStatusEncode: EncodeJson[OrderStatus] =
    EncodeJson.jencode1[OrderStatus, String](_.code)

  /**
   * Decodes a given piece of JSON into an OrderStatus object.
   * If the given string does not match any of the three valid OrderStatuses,
   * the system will fail.
   */
  val orderStatusDecode: DecodeJson[OrderStatus] =
    DecodeJson { c =>
      c.as[String].flatMap[OrderStatus] {
        case "placed" => DecodeResult.ok(Placed)
        case "approved" => DecodeResult.ok(Approved)
        case "delivered" => DecodeResult.ok(Delivered)
        case other => DecodeResult.fail(s"Unknown status: $other", c.history)
      }
    }

  /**
   * Creates a codec for OrderStatus objects.
   */
  implicit val orderStatusCodec: CodecJson[OrderStatus] =
    CodecJson.derived(orderStatusEncode, orderStatusDecode)
}
