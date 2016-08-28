package io.finch

import java.util.UUID

class DecodeSpec extends FinchSpec {
  checkAll("Decode[String]", DecodeLaws[String].all)
  checkAll("Decode[Int]", DecodeLaws[Int].all)
  checkAll("Decode[Long]", DecodeLaws[Long].all)
  checkAll("Decode[Boolean]", DecodeLaws[Boolean].all)
  checkAll("Decode[Float]", DecodeLaws[Float].all)
  checkAll("Decode[Double]", DecodeLaws[Double].all)
  checkAll("Decode[UUID]", DecodeLaws[UUID].all)
}
