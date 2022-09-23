package io.finch.refined

import cats.Id
import eu.timepit.refined.api.Refined
import eu.timepit.refined.cats._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.scalacheck.all._
import io.finch.{DecodePathLaws, FinchSpec}

class DecodePathRefinedSpec extends FinchSpec[Id] {
  checkAll("DecodePath[Int Refined Positive]", DecodePathLaws[Int Refined Positive].all)
  checkAll("DecodePath[String Refined NonEmpty]", DecodePathLaws[String Refined NonEmpty].all)
}
