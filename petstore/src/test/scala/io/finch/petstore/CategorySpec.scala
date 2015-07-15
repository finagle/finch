package io.finch.petstore

import _root_.argonaut.Argonaut._
import org.scalacheck.Prop.BooleanOperators
import argonaut.Parse
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

/*
Tests for the Category class
 */

class CategorySpec extends FlatSpec with Matchers with Checkers {
  "The Category codec" should "correctly decode JSON" in {
    val slash = """\"""
    check { (id: Long, name: String) =>
      (!name.contains(slash) && name != "\"") ==> {
        val json = s"""{ "id": $id, "name": "$name" }"""

        Parse.decodeOption[Category](json) === Some(Category(Option(id), name))
      }
     }
  }

  it should "round-trip Category" in {
    check{ cat: Category =>
      Parse.decodeOption[Category](cat.asJson.nospaces) === Some(cat)
     }
  }
}