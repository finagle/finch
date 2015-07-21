package io.finch.petstore

import _root_.argonaut._
import _root_.argonaut.Argonaut._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}

/*
Tests for Order
 */
class OrderSpec extends FlatSpec with Matchers with Checkers{

  "The Order codec" should "correctly decode JSON to Order" in {
    check{ order: Order =>
      val json = order.asJson.toString
      Parse.decodeOption[Order](json) === Some(order)
     }
  }

  it should "correctly encode an Order to JSON" in {
    check{ order: Order =>
      Parse.decodeOption[Order](order.asJson.nospaces) === Some(order)
     }
  }
}