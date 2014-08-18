package io.finch.request

import com.twitter.finagle.http.Request
import com.twitter.util.Await
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class ValidationRuleSpec extends FlatSpec {

  "A ValidationRule" should "do not throw an error if it passes validation" in {
    val request = Request(("user", "bob"))
    val user = for {
      u <- RequiredParam("user")
      _ <- ValidationRule("user", "user should not be empty") { !u.isEmpty }
    } yield u
    Await.result(user(request)) should be ("bob")
  }

  "A ValidationRule" should "throw an error if validation fails" in {
    val request = Request(("user", "not-bob"))
    val user = for {
      u <- RequiredParam("user")
      _ <- ValidationRule("user", "user should not be empty") { u == "bob" }
    } yield u
    intercept[ValidationFailed] {
      Await.result(user(request)) should be("bob")
    }
  }
}