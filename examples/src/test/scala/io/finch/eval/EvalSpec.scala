package io.finch.eval

import com.twitter.finagle.http.Status
import io.finch._
import io.finch.jackson._
import java.nio.charset.StandardCharsets
import org.scalatest.{FlatSpec, Matchers}

class EvalSpec extends FlatSpec with Matchers {
  behavior of "the eval endpoint"

  import Main._

  it should "properly evaluate a well-formed expression" in {
    val result = eval(
      Input.post("/eval")
        .withBody[Application.Json](EvalInput("10 + 10"), Some(StandardCharsets.UTF_8))
    ).awaitValueUnsafe()

    result shouldBe Some(EvalOutput("20"))
  }
  it should "give back bad request if the expression isn't parseable" in {
    val output = eval(
      Input.post("/eval")
        .withBody[Application.Json](EvalInput("s = 12"), Some(StandardCharsets.UTF_8))
    ).awaitOutputUnsafe()

    output.map(_.status) shouldBe Some(Status.BadRequest)
  }
  it should "give back nothing for other verbs" in {
    val result = eval(
      Input.get("/eval")
        .withBody[Application.Json](EvalInput("10 + 10"), Some(StandardCharsets.UTF_8))
    ).awaitValueUnsafe()

    result shouldBe None
  }
}
