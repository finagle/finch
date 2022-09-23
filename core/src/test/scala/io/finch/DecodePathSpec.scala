package io.finch

import cats.Id

import java.util.UUID

class DecodePathSpec extends FinchSpec[Id] {
  checkAll("DecodePath[Int]", DecodePathLaws[Int].all)
  checkAll("DecodePath[Long]", DecodePathLaws[Long].all)
  checkAll("DecodePath[Boolean]", DecodePathLaws[Boolean].all)
  checkAll("DecodePath[UUID]", DecodePathLaws[UUID].all)
}
