package io.finch.div

import cats.effect.unsafe.implicits.global
import com.twitter.finagle.http.Status
import io.finch.Input
import io.finch.div.Main.div
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DivSpec extends AnyFlatSpec with Matchers {
  behavior of "the div endpoint"

  it should "work if the request is a put and the divisor is not 0" in {
    div(Input.post("/20/10")).value.unsafeRunSync() shouldBe 2
  }

  it should "give back bad request if we divide by 0" in {
    div(Input.post("/20/0")).output.map(_.status).unsafeRunSync() shouldBe Some(Status.BadRequest)
  }

  it should "give back nothing for other verbs" in {
    div(Input.get("/20/10")).outputAttempt.unsafeRunSync() shouldBe a[Left[_, _]]
  }
}
