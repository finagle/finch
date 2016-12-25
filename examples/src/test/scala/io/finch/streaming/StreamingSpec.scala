package io.finch.streaming

import com.twitter.util.Await
import io.finch.Input
import org.scalatest.{FlatSpec, Matchers}

class StreamingSpec extends FlatSpec with Matchers {
  import Main._

  behavior of "the sumTo endpoint"

  it should "give back a streaming sum" in {
    sumTo(Input.post("/sumTo/3")).awaitValueUnsafe().map(s => Await.result(s.toSeq())) shouldBe
      Some(Seq(1L, 3L, 6L))
  }

  it should "give back an empty stream if param <= 0" in {
    sumTo(Input.post("/sumTo/-3")).awaitValueUnsafe().map(s => Await.result(s.toSeq())) shouldBe
      Some(Seq.empty)
  }

  it should "give back nothing for other verbs" in {
    sumTo(Input.get("/sumTo/3")).awaitValueUnsafe() shouldBe None
  }

  behavior of "the examples endpoint"

  it should "give back a stream of examples" in {
    examples(Input.get("/examples/3")).awaitValueUnsafe().map(s => Await.result(s.toSeq())) shouldBe
      Some(Seq(Example(0), Example(1), Example(2)))
  }

  it should "give back an empty stream if param <= 0" in {
    examples(Input.get("/examples/-3")).awaitValueUnsafe().map(s => Await.result(s.toSeq())) shouldBe
      Some(Seq.empty)
  }

  it should "give back nothing for other verbs" in {
    examples(Input.post("/examples/3")).awaitValueUnsafe() shouldBe None
  }
}
