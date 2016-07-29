package io.finch.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.finch.test.json.JsonCodecProviderProperties
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

class JacksonSpec extends FlatSpec with Matchers with Checkers with JsonCodecProviderProperties {

  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  "The Jackson codec provider" should "encode a case class as JSON" in encodeNestedCaseClass
  it should "decode a case class from JSON" in decodeNestedCaseClass
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson
  it should "encode a list of case class instances as JSON" in encodeCaseClassList
  it should "provide encoders with the correct content type" in checkContentType
}
