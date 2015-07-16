package io.finch.petstore

import com.twitter.finagle.{Service}
import com.twitter.finagle.httpx.{FileElement, Request, RequestBuilder, Response}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await}
import io.finch.test.ServiceSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

trait PetstoreUserServiceSuite { this: FlatSpec with ServiceSuite with Matchers =>
  def createService(): Service[Request, Response] = {
    val db = new PetstoreDb()
    val rover = Pet(None, "Rover", Nil, Some(Category(None, "dog")), Some(Seq(Tag(None, "puppy"),
      Tag(None, "white"))), Some(Available))
    db.addPet(rover)
    db.addPet(Pet(None, "Sadaharu", Nil, Some(Category(None, "inugami")), Some(Nil), Some(Available)))
    db.addPet(Pet(None, "Despereaux", Nil, Some(Category(None, "mouse")), Some(Nil), Some(Available)))
    db.addPet(Pet(None, "Alexander", Nil, Some(Category(None, "mouse")), Some(Nil), Some(Pending)))
    db.addPet(Pet(None, "Wilbur", Nil, Some(Category(None, "pig")), Some(Nil), Some(Adopted)))
    db.addPet(Pet(None, "Cheshire Cat", Nil, Some(Category(None, "cat")), Some(Nil), Some(Available)))
    db.addPet(Pet(None, "Crookshanks", Nil, Some(Category(None, "cat")), Some(Nil), Some(Available)))

    val mouseCircusOrder: Order = Order(None, Some(4), Some(100), Some("2015-07-01T17:36:58.190Z"), Some(Placed),
      Some(false))
    db.addOrder(mouseCircusOrder)

    val coraline: User = User(None, "coraline", Some("Coraline"), Some("Jones"), None, "becarefulwhatyouwishfor", None)
    db.addUser(coraline)

    endpoint.makeService(db)
  }

  //addUser
  it should "fail when trying to create a new user with the same username as an existing one" in { f =>
    val request: Request = RequestBuilder()
      .url("http://localhost:8080/user").buildPost(
        Buf.Utf8(
          s"""
             |  {
             |    "username": "coraline",
             |    "password": "independenceDei"
             |  }
           """.stripMargin))
    val result: Response = f(request)

    result.statusCode shouldBe 404
   }

  //addUsersViaArray
  it should "be able to add users via array" in { f =>
    val request: Request = RequestBuilder()
      .url("http://localhost:8080/user/createWithArray").buildPost(
        Buf.Utf8(
          s"""
             |[
             |  {
             |    "username": "portia",
             |    "password": "hamlet"
             |  }
             |]
           """.stripMargin)
      )
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  //getUser
  it should "fail when trying to find a user that doesn't exist" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/user/mortecai").buildGet
    val result: Response = f(request)
    result.statusCode shouldBe 404
   }

  //deleteUser
  it should "fail to delete users that don't exist" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/user/wyborne").buildDelete
    val result: Response = f(request)
    result.statusCode shouldBe 404
   }

  //updateUser
  it should "fail when trying to update a user that doesn't exist" in { f =>
    val request: Request = RequestBuilder()
      .url("http://localhost:8080/user/chocolate").buildPut(
        Buf.Utf8(
          s"""
             |  {
             |    "username": "chocolate",
             |    "password": "creampuff"
             |  }
           """.stripMargin))
    val result: Response = f(request)
    result.statusCode shouldBe 404
   }
}

class PetstoreUserServiceSpec extends FlatSpec with ServiceSuite with PetstoreUserServiceSuite with Matchers
