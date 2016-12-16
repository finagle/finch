package io.finch

import java.util.UUID

import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw}
import io.finch.data.Foo

class BodySpec extends FinchSpec {

  behavior of "body*"

  def withBody(b: String): Input = Input.post("/").withBody[Text.Plain](Buf.Utf8(b))

  checkAll("Body[String]", EntityEndpointLaws[String](stringBodyOption)(withBody).evaluating)
  checkAll("Body[Int]", EntityEndpointLaws[Int](stringBodyOption)(withBody).evaluating)
  checkAll("Body[Long]", EntityEndpointLaws[Long](stringBodyOption)(withBody).evaluating)
  checkAll("Body[Boolean]", EntityEndpointLaws[Boolean](stringBodyOption)(withBody).evaluating)
  checkAll("Body[Float]", EntityEndpointLaws[Float](stringBodyOption)(withBody).evaluating)
  checkAll("Body[Double]", EntityEndpointLaws[Double](stringBodyOption)(withBody).evaluating)
  checkAll("Body[UUID]", EntityEndpointLaws[UUID](stringBodyOption)(withBody).evaluating)

  it should "respond with NotFound when it's required" in {
    body[Foo, Text.Plain].apply(Input.get("/")).awaitValue() ===
      Some(Throw(Error.NotPresent(items.BodyItem)))
  }

  it should "respond with None when it's optional" in {
    body[Foo, Text.Plain].apply(Input.get("/")).awaitValue() === Some(Return(None))
  }

  it should "not match on streaming requests" in {
    val req = Request()
    req.setChunked(true)
    body[Foo, Text.Plain].apply(Input.request(req)).awaitValueUnsafe() === None
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
}
