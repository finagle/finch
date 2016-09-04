package io.finch.eval

import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import io.circe.generic.auto._
import io.circe.syntax._
import io.finch._
import org.scalatest.{FlatSpec, Matchers}

class EvalSpec extends FlatSpec with Matchers {
  behavior of "the eval endpoint"

  import Main.eval
  it should "properly evaluate a well-formed expression" in {
    val result = eval(Input.post("/eval")
      .withBody(Buf.Utf8(Main.Input("10 + 10").asJson.toString),
        Some("application/json;charset=utf8"))).value
    result shouldBe Some(Main.Output("20"))
  }
  it should "give back bad request if the expression isn't parseable" in {
    val output = eval(Input.post("/eval")
      .withBody(Buf.Utf8(Main.Input("s = 12").asJson.toString),
        Some("application/json;charset=utf8"))).output
    output.map(_.status) shouldBe Some(Status.BadRequest)
  }
  it should "give back nothing for other verbs" in {
    val result = eval(Input.get("/eval")
      .withBody(Buf.Utf8(Main.Input("10 + 10").asJson.toString),
        Some("application/json;charset=utf8"))).value
    result shouldBe None
  }
}
