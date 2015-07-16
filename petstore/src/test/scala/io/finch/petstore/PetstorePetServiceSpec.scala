package io.finch.petstore

import com.twitter.finagle.{Service}
import com.twitter.finagle.httpx.{FileElement, Request, RequestBuilder, Response}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await}
import io.finch.test.ServiceSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

trait PetstorePetServiceSuite { this: FlatSpec with ServiceSuite with Matchers =>
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

  //getPetEndpt test
  "The PetstoreApp" should "return valid pets" in { f =>
    val request = Request("/pet/1")
    val result = f(request)
    result.statusCode shouldBe 200
   }

  it should "fail to return invalid pets" in { f =>
    val request = Request("/pet/100")
    val result = Await.result(f.service(request))
    result.statusCode shouldBe 404
   }

  //addPetEndpt test
  it should "add valid pets" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet").buildPost(
          Buf.Utf8(s"""
            |  {
            |    "name": "Ell",
            |    "photoUrls":[],
            |    "category":{"name":"Wyverary"},
            |    "tags":[{"name":"Wyvern"}, {"name":"Library"}],
            |    "status":"pending"
            |  }
           """.stripMargin)
        )
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  it should "fail appropriately when adding invalid pets" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet").buildPost(
          Buf.Utf8(s"""
            |  {
            |    "id": 0,
            |    "name": "Ell",
            |    "photoUrls":[],
            |    "category":{"name":"Wyverary"},
            |    "tags":[{"name":"Wyvern"}, {"name":"Library"}],
            |    "status":"pending"
            |  }
         """.stripMargin)
        )
    val result: Response = f(request)

    result.statusCode shouldBe 404
   }

  //updatePetEndpt test
  it should "update valid pets" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet").buildPut(
          Buf.Utf8(s"""
            |{
            |  "id": 0,
            |  "name": "A-Through-L",
            |  "photoUrls":[],
            |  "category":{"name":"Wyverary"},
            |  "tags":[{"name":"Wyvern"}, {"name":"Library"}],
            |  "status":"pending"
            |}
           """.stripMargin))
    val result: Response = f(request)

    result.statusCode shouldBe 200
   }

  it should "fail attempts to update pets without specifying an ID to modify" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet").buildPut(
          Buf.Utf8(s"""
            |{
            |  "name": "A-Through-L",
            |  "photoUrls":[],
            |  "category":{"name":"Wyverary"},
            |  "tags":[{"name":"Wyvern"}, {"name":"Library"}],
            |  "status":"pending"
            |}
           """.stripMargin))
    val result: Response = f(request)

    result.statusCode shouldBe 404
   }

  //getPetsByStatusEndpt test
  it should "successfully find pets by status" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet/findByStatus?status=available")
        .buildGet
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }


  //getPetsByTagEndpt test
  it should "successfully find pets by tag" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet/findByTags?tags=puppy%2C%20white")
        .buildGet
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  //deletePetEndpt test
  it should "successfully delete existing pets" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet/0").buildDelete
    val result: Response = f(request)
    result.statusCode shouldBe 204
   }

  it should "fail to delete nonexistant pets" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet/100").buildDelete
    val result: Response = f(request)
    result.statusCode shouldBe 404
   }

  //updatePetViaForm
  it should "allow the updating of pets via form data" in { f =>
    val formData: Map[String, String] = Map("name" -> "Higgins", "status" -> "pending")

    val request: Request = RequestBuilder()
        .url("http://localhost:8080/pet/0")
        .addFormElement(("name","Higgins"))
        .addFormElement(("status","pending"))
        .buildFormPost()
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }

  //Add image
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

    //Testing for ability to add more than one file
    val totoroDataStream = getClass.getResourceAsStream("/totoro.jpg")

    val toroData: Buf = Await.result(Reader.readAll(Reader.fromStream(totoroDataStream)))

    val req: Request = RequestBuilder()
        .url("http://localhost:8080/pet/1/uploadImage")
        .add(FileElement("file", imageData))
        .buildFormPost(true)

    val outcome: Response = f(req)

    outcome.statusCode shouldBe 200
   }

  it should "be able to add an array of users" in { f =>
    val request: Request = RequestBuilder()
        .url("http://localhost:8080/user/createWithList").buildPost(
          Buf.Utf8(s"""
            |[
            |  {
            |    "username": "strawberry",
            |    "firstName": "Gintoki",
            |    "lastName": "Sakata",
            |    "email": "yorozuya@ygc.com",
            |    "password": "independenceDei"
            |  }
            |]
             """.stripMargin)
        )
    val result: Response = f(request)
    result.statusCode shouldBe 200
   }
}

class PetstorePetServiceSpec extends FlatSpec with ServiceSuite with PetstorePetServiceSuite with Matchers
