package io.finch.argonaut

import argonaut.Argonaut._
import argonaut._
import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await,Return}
import io.finch._
import io.finch.request._
import io.finch.response.TurnIntoHttp
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.scalatest.{FlatSpec, Matchers}

object ArgonautSpec {
  case class TestUser(id: Int, name: String)

  // This codec could also be made implicit
  def testUserCodec: CodecJson[TestUser] = casecodec2(TestUser.apply, TestUser.unapply)("id", "name")

  implicit def encodeTestUser: EncodeJson[TestUser] =
    EncodeJson((user: TestUser) => ("name" := user.name) ->: ("id" := user.id) ->: jEmptyObject)

  implicit def decodeTestUser: DecodeJson[TestUser] =
    DecodeJson(c => for {
      name <- (c --\ "name").as[String]
      id   <- (c --\ "id").as[Int]
    } yield TestUser(id, name))
}

class ArgonautSpec extends FlatSpec with Matchers {
  import io.finch.argonaut.ArgonautSpec._

  val str = "{\"id\":42,\"name\":\"bob\"}"
  val badJson = "{\"id\":42"
  val invalidStructure = "{\"id\":42}"
  val exampleUser = TestUser(42, "bob")

  "An ArgonautDecode" should "decode json string into a data structure" in {
    decodeArgonaut(testUserCodec)(str) shouldBe Return(exampleUser)
  }

  it should "fail if the string is not valid json" in {
    decodeArgonaut(testUserCodec)(badJson).isThrow shouldBe true
  }

  it should "fail if the decoder could not decode the string into data" in {
    decodeArgonaut(testUserCodec)(invalidStructure).isThrow shouldBe true
  }

  it should "be compatible with finch-core's requests" in {
    val req = Request()
    req.setContentString(str)
    req.setContentTypeJson()
    req.headerMap.update(HttpHeaders.Names.CONTENT_LENGTH, str.length.toString)

    val user: TestUser = Await.result(body.as[TestUser].apply(req))
    user shouldBe exampleUser
  }

  "An ArgonautEncode" should "encode a data structure into a json string" in {
    encodeArgonaut(testUserCodec)(exampleUser) shouldBe str
  }

  it should "be compatible with finch-core's responses" in {
    val service: Service[TestUser, HttpResponse] = TurnIntoHttp[TestUser]
    val result = Await.result(service(exampleUser))
    result.getContentString() shouldBe str
  }
}
