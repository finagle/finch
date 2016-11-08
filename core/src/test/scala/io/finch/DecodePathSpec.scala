package io.finch

import java.util.UUID

class DecodePathSpec extends FinchSpec {
  checkAll("DecodePath[Int]", DecodePathLaws[Int].all)
  checkAll("DecodePath[Long]", DecodePathLaws[Long].all)
  checkAll("DecodePath[Boolean]", DecodePathLaws[Boolean].all)
  checkAll("DecodePath[UUID]", DecodePathLaws[UUID].all)
}
