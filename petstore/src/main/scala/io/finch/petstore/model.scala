package io.finch.petstore

import argonaut.Argonaut._
import argonaut._

object model{

  /*
  Category case class for Pet object
   */
  case class Category(id: Long, name: String)

  /*
  Tag case class for Pet object
   */
  case class Tag(id: Long, name: String)

  /*
    A pet object with the following fields:
      id, category, name, photoUrls, tags, and status (available, pending, adopted)
   */

  case class Pet(id: Long, category: Category, name: String, photoUrls: String, tags: Tag, status: String)

  object Pet{
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def profileEncoding: EncodeJson[Pet] = jencode2L(
      (t: Pet) => (t.id, t.category, t.name, t.photoUrls, t.tags, t.status)
    )("id", "category", "name", "photoUrls", "tags", "status")
  }

  /*
    A store object with the following fields:
      id, name, inventory
   */

  case class Store(id: Long, name: String, inventory: List[Pet])

  object Store {
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def profileEncoding: EncodeJson[Store] = jencode2L(
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
    implicit def profileEncoding: EncodeJson[User] = jencode2L(
      (u: User) => (u.id, u.username, u.firstName, u.lastName, u.email, u.password, u.phone)
    )("id", "username", "firstName", "lastName", "email", "password", "phone")
  }

  // An exception that indicates missing user with `userId`.
  case class UserNotFound(userId: Long) extends Exception(s"User $userId is not found.")
}


