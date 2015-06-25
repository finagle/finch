package io.finch.petstore
import io.finch.request._

object reader{
  import model._

  implicit val petReader: RequestReader[Pet] = body.as[Pet]

  implicit val statusReader: RequestReader[String] = param("status")

  implicit val tagReader: RequestReader[Seq[String]] = param("tags").map { tags =>
    tags.split(",").map(_.trim)
  }

  implicit val nameReader: RequestReader[String] = param("name")

  /*
  A request reader that reads user objects from the http request.
  - A user is represented by url-encoded param "username"
   */
//  val userReader: RequestReader[User] =
//    param("username") should beLongerThan(4) map {
//      name => User(Id(), username, "unknown", "unknown", "unknown", "unknown", "unknown")
//    }

  /*
  RequestReader for Category
   */
//  val petCategoryReader: RequestReader[Category] = {
//    param("category") //I think this is right? Because a category is a string anyway?
//  }

  /*
  RequestReader for photoUrls
   */

  /*
  RequestReader that reads tags
   */
//  val petTagsReader: RequestReader[Seq[String]] = param("tags").map { tags =>
//    tags.split(",").map(_.trim)
//  }

  /*
  RequestReader for status
   */
}