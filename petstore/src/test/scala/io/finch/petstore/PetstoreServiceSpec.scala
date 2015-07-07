package io.finch.petstore

import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.{FileElement, Request, RequestBuilder, Response}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Await
import io.finch.argonaut._
import io.finch.petstore.endpoint._
import io.finch.test.ServiceSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

trait PetstoreServiceSuite { this: FlatSpec with ServiceSuite with Matchers =>
  def createService(): Service[Request, Response] = {
    val db = new PetstoreDb()
    db.addPet(Pet(None, "Sadaharu", Nil, Some(Category(1, "inugami")), Some(Nil), Some(Available)))
    db.addPet(Pet(None, "Despereaux", Nil, Some(Category(1, "mouse")), Some(Nil), Some(Available)))
    db.addPet(Pet(None, "Alexander", Nil, Some(Category(1, "mouse")), Some(Nil), Some(Pending)))
    db.addPet(Pet(None, "Wilbur", Nil, Some(Category(1, "pig")), Some(Nil), Some(Adopted)))
    db.addPet(Pet(None, "Cheshire Cat", Nil, Some(Category(1, "cat")), Some(Nil), Some(Available)))
    db.addPet(Pet(None, "Crookshanks", Nil, Some(Category(1, "cat")), Some(Nil), Some(Available)))

    // Add your endpoint here
    (updatePet(db) :+: getPetEndpt(db) :+: uploadImage(db)).toService
  }

  "The petstore app" should "return valid pets" in { f =>
    val request = Request("/pet/1")
    val result = f(request)

    result.statusCode shouldBe 200
  }

  it should "fail to return invalid pets" in { f =>
    val request = Request("/pet/2")
    val result = Await.result(f.service(request))

    result.statusCode shouldBe 200
  }

  it should "accept file uploads" in { f =>
    val imageDataStream = getClass.getResourceAsStream("/doge.jpg")

    //                   Buf          Future[Buf]    Reader            InputStream
    val imageData: Buf = Await.result(Reader.readAll(Reader.fromStream(imageDataStream)))

    val request: Request = RequestBuilder()
      .url("http://localhost:8080/pet/1/uploadImage")
      .add(FileElement("file", imageData))
      .buildFormPost(true)

    val result: Response = f(request)

    result.statusCode shouldBe 200
  }
}

class PetstoreServiceSpec extends FlatSpec with ServiceSuite with PetstoreServiceSuite with Matchers
