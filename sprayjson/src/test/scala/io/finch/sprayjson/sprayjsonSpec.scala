package io.finch.sprayjson

import io.finch.test.json._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import spray.json._
import DefaultJsonProtocol._

class sprayjsonSpec extends FlatSpec with Matchers with Checkers with JsonCodecProviderProperties {
  implicit val exampleFormat = jsonFormat3(ExampleCaseClass.apply)

  implicit val nestedExampleFormat = jsonFormat5(ExampleNestedCaseClass.apply)

  "The sprayjson codec provider" should "encode a case class as JSON" in encodeNestedCaseClass
  it should "decode a case class from JSON" in decodeNestedCaseClass
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson
  it should "encode a list of case class instances as JSON" in encodeCaseClassList
  it should "decode a list of case class instances from JSON" in decodeCaseClassList
  it should "provide encoders with the correct content type" in checkContentType
}
