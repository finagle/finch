package io.finch.petstore

import argonaut.{CodecJson, DecodeResult, DecodeJson, EncodeJson}

/**
 * Represents the general status of a [[Pet]]. This should either be [[Available]], [[Pending]], or [[Adopted]].
 */
sealed trait Status {
  /**
   * @return The string representing the value of this status.
   */
  def code: String
}

/**
 * The status of a [[Pet]] when it is available for adoption.
 */
case object Available extends Status {
  /**
   * @return The string representing the value of this status: "available"
   */
  def code: String = "available"
}

/**
 * The status of a [[Pet]] when it is pending for adoption, and currently unavailable for purchase.
 */
case object Pending extends Status {
  /**
   * @return The string representing the value of this status: "pending"
   */
  def code: String = "pending"
}

/**
 * The status of a [[Pet]] when it has been adopted.
 */
case object Adopted extends Status {
  /**
   * @return The string representing the value of this status: "adopted"
   */
  def code: String = "adopted"
}

/**
 * Provides encoding and decoding methods for Status objects. When given a string other than
 * "available," "pending," or "adopted," it fails to decode the string to a Status object.
 */
object Status {
  /**
   * Maps strings to their corresponding Status objects.
   * "available" => Available, "pending" => Pending, "adopted" => Adopted
   * @return Status object corresponding to passed-in String s.
   */
  def fromString(s: String): Status = s match {
    case "available" => Available
    case "pending" => Pending
    case "adopted" => Adopted
  }

  /**
   * Takes a Status and returns the corresponding string value in JSON.
   */
  val statusEncode: EncodeJson[Status] =
    EncodeJson.jencode1[Status, String](_.code)

  /**
   * Takes JSON and gives back its corresponding Status.
   * If the given string is not one of the three valid statuses, the system will fail.
   */
  val statusDecode: DecodeJson[Status] =
    DecodeJson { c =>
      c.as[String].flatMap[Status] {
        case "available" => DecodeResult.ok(Available)
        case "pending" => DecodeResult.ok(Pending)
        case "adopted" => DecodeResult.ok(Adopted)
        case other => DecodeResult.fail(s"Unknown status: $other", c.history)
      }
    }

  /**
   * Creates the codec for the Status object.
   */
  implicit val statusCodec: CodecJson[Status] =
    CodecJson.derived(statusEncode, statusDecode)
}
