package io.finch.json.argonaut

import argonaut.Argonaut._
import argonaut._
import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Future, Await}
import io.finch._
import io.finch.json.TurnJsonIntoHttp
import io.finch.request.RequiredJsonBody
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
  import io.finch.json.argonaut.ArgonautSpec._

  val str = "{\"id\":42,\"name\":\"bob\"}"
  val badJson = "{\"id\":42"
  val invalidStructure = "{\"id\":42}"
  val exampleUser = TestUser(42, "bob")

  "An ArgonautDecode" should "decode json string into a data structure" in {
    toArgonautDecode(testUserCodec)(str) shouldEqual Option(exampleUser)
  }

  it should "return None if the string is not valid json" in {
    toArgonautDecode(testUserCodec)(badJson) shouldEqual None
  }

  it should "return None if the decoder could not decode the string into data" in {
    toArgonautDecode(testUserCodec)(invalidStructure) shouldEqual None
  }

  it should "be compatible with finch-core's requests" in {
    val req = Request()
    req.setContentString(str)
    req.setContentTypeJson()
    req.headerMap.update(HttpHeaders.Names.CONTENT_LENGTH, str.length.toString)

    val user: TestUser = Await.result(RequiredJsonBody(req))
    user shouldEqual exampleUser
  }

  "An ArgonautEncode" should "encode a data structure into a json string" in {
    toArgonautEncode(testUserCodec)(exampleUser) shouldEqual str
  }

  it should "be compatible with finch-core's responses" in {
    val service: Service[TestUser, HttpResponse] = TurnJsonIntoHttp[TestUser]
    val result = Await.result(service(exampleUser))
    result.getContentString() shouldEqual str
  }
}