package io.finch.petstore

import _root_.argonaut._
import _root_.argonaut.Argonaut._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}

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