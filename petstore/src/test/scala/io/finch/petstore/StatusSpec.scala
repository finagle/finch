package io.finch.petstore

import _root_.argonaut.Argonaut._
import org.scalacheck.Prop.BooleanOperators
import argonaut.Parse
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}


/*
Tests for the Status class
 */
class StatusSpec extends FlatSpec with Matchers with Checkers {
  "The Status codec" should "correctly decode JSON" in {
    Parse.decodeOption[Status]("\"available\"") shouldBe Some(Available)
    Parse.decodeOption[Status]("\"pending\"") shouldBe Some(Pending)
    Parse.decodeOption[Status]("\"adopted\"") shouldBe Some(Adopted)
  }

  it should "fail to decode irrelevant JSON" in {
    Parse.decodeOption[Status]("\"foo\"") shouldBe None

    check{ (randString: String) =>
      !List("available", "pending", "adopted)").contains(randString) ==> {
        Parse.decodeOption[Status]("\"randString\"") === None
      }
     }
  }

  it should "correctly encode Status objects" in {
    check{ stat: Status =>
      Parse.decodeOption[Status](stat.asJson.nospaces) === Some(stat)
     }
  }
}