package io.finch.petstore

import _root_.argonaut._
import _root_.argonaut.Argonaut._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.BooleanOperators
import org.scalatest.{FlatSpec, Matchers}
//import org.scalatest.prop.Checkers

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

/*
Tests for the Status class
 */
class StatusSpec extends FlatSpec with Matchers with Checkers {

  "The Status codec" should "correctly fail to decode irrelevant JSON" in {
    Parse.decodeOption[Status]("available") === Available
    Parse.decodeOption[Status]("pending") === Pending
    Parse.decodeOption[Status]("adopted") === Adopted
    Parse.decodeOption[Status]("") ===
    check{(randString: String) =>
      (!List("available", "pending", "adopted)").contains(randString)) ==> {
        Parse.decodeOption[Status](randString) === None
//        Parse.decodeOption[Status](randString) === s"Unknown status: $randString"
      }
    }
  }

  it should "correctly encode Status objects" in {
    check{stat: Status =>
      Parse.decodeOption[Status](stat.asJson.nospaces) === Some(stat)
    }
  }
}

/*
Tests for the Category class
 */

class CategorySpec extends FlatSpec with Matchers with Checkers {
  "The Category codec" should "correctly decode JSON" in {
    val slash = """\"""
    check { (id: Long, name: String) =>
      (!name.contains(slash)) ==> {
        val json = s"""{ "id": $id, "name": "$name" }"""

        Parse.decodeOption[Category](json) === Some(Category(id, name))
      }
    }
  }

  it should "round-trip Category" in { //Is this encoding? yes.
    check{cat: Category =>
      Parse.decodeOption[Category](cat.asJson.nospaces) === Some(cat)
    }
  }
}

/*
Tests for the Tag class
 */
class TagSpec extends FlatSpec with Matchers with Checkers{
  "The Tag codec" should "correctly decode JSON" in {
    check{ (id: Long, name: String) =>
      (!name.contains("\"")) ==> {
        val json = s"""{ "id": $id, "name": "$name" }"""
        Parse.decodeOption[Tag](json) === Some(Tag(id, name))
      }

    }
  }

  it should "correctly encode Tag objects" in {
    check{t:Tag =>
      Parse.decodeOption[Tag](t.asJson.nospaces) === Some(t)
    }
  }
 }