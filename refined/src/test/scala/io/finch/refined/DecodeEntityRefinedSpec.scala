package io.finch.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.cats._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import io.finch.{ DecodeEntityLaws, FinchSpec }

class DecodeEntityRefinedSpec extends FinchSpec {

  checkAll("DecodeEntity[Int Refined Positive]", DecodeEntityLaws[Int Refined Positive].all)
  checkAll("DecodeEntity[String Refined NonEmpty]", DecodeEntityLaws[String Refined NonEmpty].all)

}
