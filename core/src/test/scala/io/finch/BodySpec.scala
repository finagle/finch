package io.finch

import java.util.UUID

import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw}
import io.finch.data.Foo

class BodySpec extends FinchSpec {

  behavior of "body*"

  def withBody(b: String): Input = Input.post("/").withBody[Text.Plain](Buf.Utf8(b))

  /*checkAll("Body[Int]", EntityEndpointLaws[Int](textBodyOption)(withBody).evaluating)
  checkAll("Body[Long]", EntityEndpointLaws[Long](textBodyOption)(withBody).evaluating)
  checkAll("Body[Boolean]", EntityEndpointLaws[Boolean](textBodyOption)(withBody).evaluating)
  checkAll("Body[Float]", EntityEndpointLaws[Float](textBodyOption)(withBody).evaluating)
  checkAll("Body[Double]", EntityEndpointLaws[Double](textBodyOption)(withBody).evaluating)
  checkAll("Body[UUID]", EntityEndpointLaws[UUID](textBodyOption)(withBody).evaluating)*/

  it should "respond with NotFound when it's required" in {
    body[Foo, Text.Plain].apply(Input.get("/")).awaitValue() shouldBe
      Some(Throw(Error.NotPresent(items.BodyItem)))
  }

  it should "respond with None when it's optional" in {
    bodyOption[Foo, Text.Plain].apply(Input.get("/")).awaitValue() shouldBe Some(Return(None))
  }

  it should "not match on streaming requests" in {
    val req = Request()
    req.setChunked(true)
    body[Foo, Text.Plain].apply(Input.fromRequest(req)).awaitValueUnsafe() shouldBe None
  }

  it should "respond with a value when present and required" in {
    check { f: Foo =>
      val i = Input.post("/").withBody[Text.Plain](f)
      body[Foo, Text.Plain].apply(i).awaitValueUnsafe() === Some(f)
    }
  }

  it should "respond with Some(value) when it's present and optional" in {
    check { f: Foo =>
      val i = Input.post("/").withBody[Text.Plain](f)
      bodyOption[Foo, Text.Plain].apply(i).awaitValueUnsafe().flatten === Some(f)
    }
  }

  it should "treat 0-length bodies as empty" in {
    val i = Input.post("/").withHeaders("Content-Length" -> "0")

    bodyOption[Foo, Text.Plain].apply(i).awaitValueUnsafe().flatten shouldBe None
    stringBodyOption(i).awaitValueUnsafe().flatten shouldBe None
    binaryBodyOption(i).awaitValueUnsafe().flatten shouldBe None
  }
}
