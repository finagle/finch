package io.finch.petstore

import argonaut.Argonaut._
import argonaut._
import com.twitter.util.Future

object model{

  /*
  Category case class for Pet object
   */
  case class Category(id: Long, name: String) //Not sure if this needs to be a class....

  /*
  Tag case class for Pet object
   */
  case class Tag(id: Long, name: String)

  /*
  Status case class for Pet object
   */
//  case class Status(state: String)

  /*
    A pet object with the following fields:
      id, category, name, photoUrls, tags, and status (available, pending, adopted)
   */

  case class Pet(
      id: Option[Long],
      category: Option[Category],
      name: Future[String],
      photoUrls: Future[Seq[String]],
      tags: Option[Seq[Tag]],
      status: Option[String] //available, pending, adopted
      )

  object Pet{
    // Provides an implementation of the EncodeJson type class from Argonaut
//    implicit def profileEncoding: EncodeJson[Pet] = jencode6L(
//      (t: Pet) => (t.id, t.category, t.name, t.photoUrls, t.tags, t.status)
//    )("id", "category", "name", "photoUrls", "tags", "status")

    implicit def petCodec: CodecJson[Pet] = //instance of a type class
      casecodec6(Pet.apply, Pet.unapply)("id", "category", "name", "photoUrls", "tags", "status")

    //you get apply and unapply for free remember? When you have companion objects

//    def apply(idIn:Long,
//        catIn: Category,
//        nameIn: String,
//        photoIn: Seq[String],
//        tagsIn: Seq[String],
//        statusIn: String) = new Pet(idIn, catIn, nameIn, photoIn, tagsIn, statusIn)
//
//    def unapply(p:Pet) = {
//      if (p == null) None
//      else Some(p.id, p.category, p.name, p.photoUrls, p.tags, p.status)
//    }
  }

  /*
    A store object with the following fields:
      id, name, inventory
   */

  case class Store(id: Long, name: String, inventory: List[Pet])

  object Store {
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def profileEncoding: EncodeJson[Store] = jencode3L(
      (s: Store) => (s.id, s.name, s.inventory)
    )("id", "name", "inventory")
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


