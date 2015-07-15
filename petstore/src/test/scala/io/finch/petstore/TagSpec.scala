package io.finch.petstore

import _root_.argonaut._
import _root_.argonaut.Argonaut._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.BooleanOperators
import org.scalatest.{FlatSpec, Matchers}

/*
Tests for the Tag class
 */
class TagSpec extends FlatSpec with Matchers with Checkers{
  "The Tag codec" should "correctly decode JSON" in {
    check{ (id: Long, name: String) =>
      (!name.contains("\"") && !name.contains("\\")) ==> {
        val json = s"""{ "id": $id, "name": "$name" }"""
        Parse.decodeOption[Tag](json) === Some(Tag(Option(id), name))
      }
     }
  }

  it should "correctly encode Tag objects" in {
    check{ t:Tag =>
      Parse.decodeOption[Tag](t.asJson.nospaces) === Some(t)
     }
  }
}