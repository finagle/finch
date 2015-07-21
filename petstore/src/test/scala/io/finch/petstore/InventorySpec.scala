package io.finch.petstore

import _root_.argonaut._
import _root_.argonaut.Argonaut._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}

/*
Tests for Inventory
 */
class InventorySpec extends FlatSpec with Matchers with Checkers{
  "The Inventory codec" should "correctly decode JSON to Inventory" in {
    check { i: Inventory =>
      val json = i.asJson.toString
      Parse.decodeOption[Inventory](json) === Some(i)
     }
  }
  it should "correctly encode an Inventory to JSON" in {
    check{ i: Inventory =>
      Parse.decodeOption[Inventory](i.asJson.nospaces) === Some(i)
     }
  }
}