package io.finch

import java.util.UUID

class DecodeEntitySpec extends FinchSpec {
  checkAll("DecodeEntity[String]", DecodeEntityLaws[String].all)
  checkAll("DecodeEntity[Int]", DecodeEntityLaws[Int].all)
  checkAll("DecodeEntity[Long]", DecodeEntityLaws[Long].all)
  checkAll("DecodeEntity[Boolean]", DecodeEntityLaws[Boolean].all)
  checkAll("DecodeEntity[Float]", DecodeEntityLaws[Float].all)
  checkAll("DecodeEntity[Double]", DecodeEntityLaws[Double].all)
  checkAll("DecodeEntity[UUID]", DecodeEntityLaws[UUID].all)
}
