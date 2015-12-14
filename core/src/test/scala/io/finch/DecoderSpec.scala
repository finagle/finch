package io.finch

import java.util.UUID

class DecoderSpec extends FinchSpec {
  checkAll("Decoder[String]", DecoderLaws[String].all)
  checkAll("Decoder[Int]", DecoderLaws[Int].all)
  checkAll("Decoder[Long]", DecoderLaws[Long].all)
  checkAll("Decoder[Boolean]", DecoderLaws[Boolean].all)
  checkAll("Decoder[Float]", DecoderLaws[Float].all)
  checkAll("Decoder[Double]", DecoderLaws[Double].all)
  checkAll("Decoder[UUID]", DecoderLaws[UUID].all)
}
