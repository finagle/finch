package io.finch.json.argonaut

import argonaut._, Argonaut._
import org.scalatest.{Matchers, FlatSpec}

class ArgonautSpec extends FlatSpec with Matchers {

  case class TestUser(id: Int, name: String)
  def testUserCodec: CodecJson[TestUser] = casecodec2(TestUser.apply, TestUser.unapply)("id", "name")

  val str = "{\"id\":42,\"name\":\"bob\"}"
  val badJson = "{\"id\":42"
  val invalidStructure = "{\"id\":42}"
  val exampleUser = TestUser(42, "bob")

  "An ArgonautDecode" should "decode json string into a data structure" in {
    toArgonautDecode(testUserCodec)(str) shouldEqual Option(exampleUser)
  }

  "An ArgonautDecode" should "return None if the string is not valid json" in {
    toArgonautDecode(testUserCodec)(badJson) shouldEqual None
  }

  "An ArgonautDecode" should "return None if the decoder could not decode the string into data" in {
    toArgonautDecode(testUserCodec)(invalidStructure) shouldEqual None
  }

  "An ArgonautEncode" should "encode a data structure into a json string" in {
    toArgonautEncode(testUserCodec)(exampleUser) shouldEqual str
  }
}