package io.finch.petstore

import com.twitter.finagle.{Service}
import com.twitter.finagle.httpx.{FileElement, Request, RequestBuilder, Response}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await}
import io.finch.test.ServiceSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

trait PetstoreStoreServiceSuite { this: FlatSpec with ServiceSuite with Matchers =>
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

  //getInventory
  it should "be able to return the current inventory" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/store/inventory").buildGet()
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  //addOrder
  it should "be able to add new orders to the store" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/store/order").buildPost(
          Buf.Utf8(
            s"""
               |{
               |  "petId":0,
               |  "quantity":5,
               |  "status":"placed"
               |}
             """.stripMargin
          )
        )
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  //deleteOrder
  it should "be able to delete orders from the store" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/store/order/0").buildDelete
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  //findOrder
  it should "fail when searching for an order that doesn't exist" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/store/order/10").buildGet
    val result: Response = f(request)
    result.statusCode shouldBe 404
   }
}

class PetstoreStoreServiceSpec extends FlatSpec with ServiceSuite with PetstoreStoreServiceSuite with Matchers
