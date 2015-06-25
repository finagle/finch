package io.finch.petstore

import argonaut.Argonaut._
import argonaut._
import com.twitter.util.Future

object model{

  case class Category(id: Long, name: String) //Not sure if this needs to be a class....

  case class Tag(id: Long, name: String)

  //Begin Status

  sealed trait Status {
    def code: String
  }

  case object Available extends Status {
    def code: String = "available"
  }

  case object Pending extends Status {
    def code: String = "pending"
  }

  case object Sold extends Status {
    def code: String = "sold"
  }

  object Status {
    def fromString(s: String): Status = s match {
      case "available" => Available
      case "pending" => Pending
      case "sold" => Sold
    }

    val statusEncode: EncodeJson[Status] =
      EncodeJson.jencode1[Status, String](_.code)

    val statusDecode: DecodeJson[Status] =
      DecodeJson { c =>
        c.as[String].flatMap[Status] {
          case "available" => DecodeResult.ok(Available)
          case "pending" => DecodeResult.ok(Pending)
          case "sold" => DecodeResult.ok(Sold)
          case other => DecodeResult.fail(s"Unknown status: $other", c.history)
        }
      }

    implicit val statusCodec: CodecJson[Status] =
      CodecJson.derived(statusEncode, statusDecode)
  }

  //End Status

  /*
    A pet object with the following fields:
      id, category, name, photoUrls, tags, and status (available, pending, adopted)
   */

  case class Pet(
      id: Long,
      category: Option[Category],
      name: String,
      photoUrls: Seq[String],
      tags: Option[Seq[Tag]],
      status: Option[Status] //available, pending, adopted
      )

  object Pet{
    implicit def petCodec: CodecJson[Pet] = //instance of a type class
      casecodec6(Pet.apply, Pet.unapply)("id", "category", "name", "photoUrls", "tags", "status")
  }

  /*
  Store attributes
   */
  case class Inventory(available: Long, pending: Long, adopted: Long)

  case class Store(name: String, inventory: Inventory)

  object Store {
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def profileEncoding: EncodeJson[Store] = jencode2L(
      (s: Store) => (s.name, s.inventory)
    )("name", "inventory")
  }

  /*
    A user object with the following fields:
      id, username, firstName, lastName, email, password, phone
   */

  case class User(id: Long, username: String, firstName: String, lastName: String,
      email: String, password: String, phone: String)

  object User{
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def profileEncoding: EncodeJson[User] = jencode7L(
      (u: User) => (u.id, u.username, u.firstName, u.lastName, u.email, u.password, u.phone)
    )("id", "username", "firstName", "lastName", "email", "password", "phone")
  }

  // An exception that indicates missing user with `userId`.
  case class UserNotFound(userId: Long) extends Exception(s"User $userId is not found.")
}


