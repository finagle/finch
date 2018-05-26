package io.finch

import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw, Try}
import io.finch.data.Foo
import java.nio.charset.Charset

class BodySpec extends FinchSpec {

  private class EvalDecode[A](d: Decode.Text[A]) extends Decode[A] {
    type ContentType = Text.Plain

    @volatile private var e = false

    def apply(b: Buf, cs: Charset): Try[A] = {
      e = true
      d(b, cs)
    }

    def evaluated: Boolean = e
  }

  behavior of "body*"

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

  it should "respond with Some(value) when it'ss present and optional" in {
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

  it should "never evaluate until run" in {
    check { f: Foo =>
      val i = Input.post("/").withBody[Text.Plain](f)

      implicit val ed = new EvalDecode[Foo](Decode[Foo, Text.Plain])
      textBody[Foo].apply(i)
      !ed.evaluated
    }
  }
}
