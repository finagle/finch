package io.finch.petstore

import _root_.argonaut._
import _root_.argonaut.Argonaut._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}

/*
Tests for the Pet class
 */
class PetSpec extends FlatSpec with Matchers with Checkers {

  "The Pet codec" should "correctly decode JSON to Pet" in {
    check{ pet: Pet =>
      val json = pet.asJson.toString
      Parse.decodeOption[Pet](json) === Some(pet)
     }
  }

  it should "correctly encode a Pet to JSON" in {
    check{ pet: Pet =>
      Parse.decodeOption[Pet](pet.asJson.nospaces) === Some(pet)
     }
  }
}

