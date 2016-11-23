package io.finch.playjson

import io.finch.test.json.ExampleCaseClass
import io.finch.test.json.ExampleNestedCaseClass
import io.finch.test.json.JsonCodecProviderProperties
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers
import play.api.libs.json._

class PlayJsonSpec extends FlatSpec with Matchers with Checkers with JsonCodecProviderProperties {

  implicit val exampleReads: Reads[ExampleCaseClass] =
    Json.reads[ExampleCaseClass]
  implicit val exampleWrites: Writes[ExampleCaseClass] =
    Json.writes[ExampleCaseClass]
  implicit val nestedExampleReads: Reads[ExampleNestedCaseClass] =
    Json.reads[ExampleNestedCaseClass]
  implicit val nestedExampleWrites: Writes[ExampleNestedCaseClass] =
    Json.writes[ExampleNestedCaseClass]

  "The PlayJson codec provider" should "encode a case class as JSON" in encodeNestedCaseClass
  it should "decode a case class from JSON" in decodeNestedCaseClass
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson
  it should "encode a list of case class instances as JSON" in encodeCaseClassList
  it should "decode a list of case class instances from JSON" in decodeCaseClassList
  it should "provide encoders with the correct content type" in checkContentType
  it should "encode an exception" in encodeException
}
