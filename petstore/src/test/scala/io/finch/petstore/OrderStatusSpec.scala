package io.finch.petstore

import _root_.argonaut.Argonaut._
import org.scalacheck.Prop.BooleanOperators
import argonaut.Parse
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}

/*
Tests for OrderStatus
 */
class OrderStatusSpec extends FlatSpec with Matchers with Checkers{
  "The OrderStatus codec" should "correctly fail to decode irrelevant JSON" in {
    Parse.decodeOption[OrderStatus]("\"placed\"") === Placed
    Parse.decodeOption[OrderStatus]("\"approved\"") === Approved
    Parse.decodeOption[OrderStatus]("\"delivered\"") === Delivered
    //    Parse.decodeOption[OrderStatus]("") === None
    check{ (randString: String) =>
      (!List("placed", "approved", "delivered").contains(randString)) ==> {
        Parse.decodeOption[OrderStatus]("\"randString\"") === None
      }
     }
  }

  it should "correctly encode OrderStatus objects" in {
    check{ ordStat: OrderStatus =>
      Parse.decodeOption[OrderStatus](ordStat.asJson.nospaces) === Some(ordStat)
     }
  }
}