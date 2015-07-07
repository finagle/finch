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
    Parse.decodeOption[Status]("\"available\"") shouldBe Some(Available)
    Parse.decodeOption[Status]("\"pending\"") shouldBe Some(Pending)
    Parse.decodeOption[Status]("\"adopted\"") shouldBe Some(Adopted)
    Parse.decodeOption[Status]("") shouldBe None

    check{(randString: String) =>
      (!List("available", "pending", "adopted)").contains(randString)) ==> {
        Parse.decodeOption[Status](randString) === None
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

  it should "round-trip Category" in {
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
      (!name.contains("\"") && !name.contains("\\")) ==> {
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

/*
Tests for OrderStatus
 */
class OrderStatusSpec extends FlatSpec with Matchers with Checkers{
  "The OrderStatus codec" should "correctly fail to decode irrelevant JSON" in {
    Parse.decodeOption[OrderStatus]("placed") === Placed
    Parse.decodeOption[OrderStatus]("approved") === Approved
    Parse.decodeOption[OrderStatus]("delivered") === Delivered
    Parse.decodeOption[OrderStatus]("") === None
      check{(randString: String) =>
        (!List("placed", "approved", "delivered").contains(randString)) ==> {
          Parse.decodeOption[OrderStatus](randString) === None
        }
      }
  }

  it should "correctly encode OrderStatus objects" in {
    check{ordStat: OrderStatus =>
      Parse.decodeOption[OrderStatus](ordStat.asJson.nospaces) === Some(ordStat)
    }
  }
}

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

/*
Tests for User
 */
class UserSpec extends FlatSpec with Matchers with Checkers{

  "The User codec" should "correctly decode JSON to User" in {
    check { u: User =>
      val json = u.asJson.toString
      Parse.decodeOption[ User ](json) === Some(u)
    }
  }

  it should "correctly encode a User to JSON" in {
    check{ u: User =>
      Parse.decodeOption[User](u.asJson.nospaces) === Some(u)
    }
  }
}

