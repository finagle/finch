package io.finch.json4s

import io.finch.test.json.JsonCodecProviderProperties
import org.json4s.DefaultFormats
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers

class Json4sSpec extends FlatSpec with Matchers with Checkers with JsonCodecProviderProperties {
  implicit val formats = DefaultFormats

  "The Json4s codec provider" should "encode a case class as JSON" in encodeNestedCaseClass
  it should "decode a case class from JSON" in decodeNestedCaseClass
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson
  it should "encode a list of case class instances as JSON" in encodeCaseClassList
  it should "decode a list of case class instances from JSON" in decodeCaseClassList
  it should "provide encoders with the correct content type" in checkContentType
}
