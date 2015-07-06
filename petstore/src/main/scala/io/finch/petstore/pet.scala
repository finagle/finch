package io.finch.petstore

import _root_.argonaut._
import argonaut.Argonaut._

case class Pet(
    id: Option[Long],
    name: String,
    photoUrls: Seq[String],
    category: Option[Category],
    tags: Option[Seq[Tag]],
    status: Option[Status] //available, pending, adopted
    )

object Pet {
  implicit val petCodec: CodecJson[Pet] = //instance of a type class
    casecodec6(Pet.apply, Pet.unapply)("id", "name", "photoUrls", "category", "tags", "status")
}

/*
STATUS THINGS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */
sealed trait Status {
  def code: String
}

case object Available extends Status {
  def code: String = "available"
}

case object Pending extends Status {
  def code: String = "pending"
}

case object Adopted extends Status {
  def code: String = "adopted"
}

object Status {
  def fromString(s: String): Status = s match {
    case "available" => Available
    case "pending" => Pending
    case "adopted" => Adopted
  }

  val statusEncode: EncodeJson[Status] =
    EncodeJson.jencode1[Status, String](_.code)

  val statusDecode: DecodeJson[Status] =
    DecodeJson { c =>
      c.as[String].flatMap[Status] {
        case "available" => DecodeResult.ok(Available)
        case "pending" => DecodeResult.ok(Pending)
        case "adopted" => DecodeResult.ok(Adopted)
        case other => DecodeResult.fail(s"Unknown status: $other", c.history)
      }
    }

  implicit val statusCodec: CodecJson[Status] =
    CodecJson.derived(statusEncode, statusDecode)
}

/*
STATUS THINGS END HERE========================================================
 */

/*
CATEGORY THINGS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

case class Category(id: Long, name: String)

object Category {
  implicit val categoryCodec: CodecJson[Category] =
    casecodec2(Category.apply, Category.unapply)("id", "name")
}
/*
CATEGORY THINGS END HERE========================================================
 */

/*
TAG THINGS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */
case class Tag(id: Long, name: String)

object Tag {

  implicit val tagCodec: CodecJson[Tag] =
    casecodec2(Tag.apply, Tag.unapply)("id", "name")
}
/*
TAG THINGS END HERE========================================================
 */


/*
UPLOAD THINGS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */
case class UploadResponse(
    code: Long,
    responseType: String,
    message: String
    )

object UploadResponse {
  implicit val uploadResponseCodec: CodecJson[UploadResponse] =
    casecodec3(UploadResponse.apply, UploadResponse.unapply)("code", "type", "message")
}
/*
UPLOAD THINGS END HERE========================================================
 */

/*
ORDERSTATUS THINGS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */
sealed trait Status {
  def code: String
}

case object Available extends Status {
  def code: String = "available"
}

case object Pending extends Status {
  def code: String = "pending"
}

case object Adopted extends Status {
  def code: String = "adopted"
}

object Status {
  def fromString(s: String): Status = s match {
    case "available" => Available
    case "pending" => Pending
    case "adopted" => Adopted
  }

  val statusEncode: EncodeJson[Status] =
    EncodeJson.jencode1[Status, String](_.code)

  val statusDecode: DecodeJson[Status] =
    DecodeJson { c =>
      c.as[String].flatMap[Status] {
        case "available" => DecodeResult.ok(Available)
        case "pending" => DecodeResult.ok(Pending)
        case "adopted" => DecodeResult.ok(Adopted)
        case other => DecodeResult.fail(s"Unknown status: $other", c.history)
      }
    }

  implicit val statusCodec: CodecJson[Status] =
    CodecJson.derived(statusEncode, statusDecode)
}

/*
ORDERSTATUS THINGS END HERE========================================================
 */

/*
ORDER THINGS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

case class Order(
    id: Option[Long],
    petId: Option[Long],
    quantity: Option[Long],
    shipDate: Option[String],
    status: Option[OrderStatus], //placed, approved, delivered
    complete: Option[Boolean]
    )

object Order {
  implicit val orderCodec: CodecJson[Order] =
    casecodec6(Order.apply, Option.unapply)("id", "petId", "quantity", "shipDate", "status", "complete")
}
/*
ORDER THINGS END HERE========================================================
 */