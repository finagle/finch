package io.finch.pickling

import org.apache.commons.codec.binary.Base64

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers

class PicklingBinarySpec extends FlatSpec with Matchers with Checkers with PicklingSerialCodecProperties {

  import scala.pickling.Defaults._
  import scala.pickling.binary._
  import io.finch.pickling.Converters.pickleStringWriter

  private def byteMaker(input: String) = {
    Base64.decodeBase64(input)
  }

  implicit def c(input: String): Array[Byte] = byteMaker(input)

  implicit def x(input: String): BinaryPickle =
    Converters.binaryStringReader(input)(c)

  def bsr(input: String): BinaryPickle = x(input)

  "The Pickling codec provider" should "encode a case class as JSON" in encodeNestedCaseClass(
    implicitly, bsr, implicitly
  )
  it should "decode a case class from JSON" in decodeNestedCaseClass(
    implicitly, pickleStringWriter, bsr, implicitly
  )
  it should "properly fail to decode invalid JSON into a case class" in failToDecodeInvalidJson(
    implicitly, bsr, implicitly
  )
  it should "provide encoders with the correct content type" in checkContentType
}
