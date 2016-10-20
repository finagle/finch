package io.finch.argonaut

import argonaut._
import argonaut.Argonaut._
import io.finch.test.json._
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers

class ArgonautSpec extends FlatSpec with Matchers with Checkers with JsonCodecProviderProperties {

  implicit val exampleCaseClassCodecJson: CodecJson[ExampleCaseClass] =
    casecodec3(ExampleCaseClass.apply, ExampleCaseClass.unapply)("a", "b", "c")

 implicit val exampleNestedCaseClassCodecJson: CodecJson[ExampleNestedCaseClass] =
    casecodec5(ExampleNestedCaseClass.apply, ExampleNestedCaseClass.unapply)(
      "string",
      "double",
      "long",
      "ints",
      "example"
    )

  "The Argonaut codec provider" should "encode a case class as JSON" in encodeNestedCaseClass
  it should "decode a case class from JSON" in decodeNestedCaseClass
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson
  it should "encode a list of case class instances as JSON" in encodeCaseClassList
  it should "decode a list of case class instances from JSON" in decodeCaseClassList
  it should "provide encoders with the correct content type" in checkContentType
}
