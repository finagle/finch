package io.finch.div

import com.twitter.finagle.http.Status
import io.finch.Input
import org.scalatest.{FlatSpec, Matchers}

class DivSpec extends FlatSpec with Matchers {
  behavior of "the div endpoint"

  import Main.div
  it should "work if the request is a put and the divisor is not 0" in {
    div(Input.post("/20/10")).awaitValueUnsafe() shouldBe Some(2)
  }
  it should "give back bad request if we divide by 0" in {
    div(Input.post("/20/0")).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.BadRequest)
  }
  it should "give back nothing for other verbs" in {
    div(Input.get("/20/10")).awaitValueUnsafe() shouldBe None
  }
}
