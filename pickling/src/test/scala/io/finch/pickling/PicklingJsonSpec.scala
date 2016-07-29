package io.finch.pickling

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers


class PicklingJsonSpec extends FlatSpec with Matchers with Checkers with PicklingSerialCodecProperties {

  import scala.pickling.Defaults._
  import scala.pickling.json._
  import io.finch.pickling.Converters.pickleStringWriter

  "The Pickling codec provider" should "encode a case class as JSON" in encodeNestedCaseClass(
    implicitly, Converters.jsonStringReader, implicitly
  )
  it should "decode a case class from JSON" in decodeNestedCaseClass(
    implicitly, pickleStringWriter, Converters.jsonStringReader, implicitly
  )
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson(
    implicitly, Converters.jsonStringReader, implicitly
  )
  it should "provide encoders with the correct content type" in checkContentType
}
