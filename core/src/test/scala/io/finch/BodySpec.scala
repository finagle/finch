package io.finch

import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import io.finch.data.Foo
import shapeless.{:+:, CNil}

import java.nio.charset.Charset

class BodySpec extends FinchSpec {

  private class EvalDecode[A](d: Decode.Text[A]) extends Decode[A] {
    type ContentType = Text.Plain

    @volatile private var e = false

    def apply(b: Buf, cs: Charset): Either[Throwable, A] = {
      e = true
      d(b, cs)
    }

    def evaluated: Boolean = e
  }

  behavior of "body*"

  it should "respond with NotFound when it's required" in {
    val b = body[Foo, Text.Plain].apply(Input.get("/"))
    b.valueAttempt.unsafeRunSync() shouldBe Left(Error.BodyNotPresent)
  }

  it should "respond with None when it's optional" in {
    bodyOption[Foo, Text.Plain].apply(Input.get("/")).value.unsafeRunSync() shouldBe None
  }

  it should "not match on streaming requests" in {
    val req = Request()
    req.setChunked(true)
    body[Foo, Text.Plain].apply(Input.fromRequest(req)).isMatched shouldBe false
  }

  it should "respond with a value when present and required" in {
    check { f: Foo =>
      val i = Input.post("/").withBody[Text.Plain](f)
      body[Foo, Text.Plain].apply(i).value.unsafeRunSync() === f
    }
  }

  it should "respond with Some(value) when it'ss present and optional" in {
    check { f: Foo =>
      val i = Input.post("/").withBody[Text.Plain](f)
      bodyOption[Foo, Text.Plain].apply(i).value.unsafeRunSync() === Some(f)
    }
  }

  it should "treat 0-length bodies as empty" in {
    val i = Input.post("/").withHeaders("Content-Length" -> "0")

    bodyOption[Foo, Text.Plain].apply(i).value.unsafeRunSync() shouldBe None
    stringBodyOption.apply(i).value.unsafeRunSync() shouldBe None
    binaryBodyOption.apply(i).value.unsafeRunSync() shouldBe None
  }

  it should "never evaluate until run" in {
    check { f: Foo =>
      val i = Input.post("/").withBody[Text.Plain](f)
      implicit val ed: EvalDecode[Foo] = new EvalDecode[Foo](Decode[Foo, Text.Plain])
      textBody[Foo].apply(i)
      !ed.evaluated
    }
  }

  it should "respect Content-Type header and pick corresponding decoder for coproduct" in {
    check { f: Foo =>
      val plain = Input.post("/").withBody[Text.Plain](f)
      val csv = Input.post("/").withBody[Application.Csv](f)
      val endpoint = body[Foo, Text.Plain :+: Application.Csv :+: CNil]
      endpoint(plain).value.unsafeRunSync() === f && endpoint(csv).value.unsafeRunSync() === f
    }
  }

  it should "resolve into NotParsed(Decode.UMTE) if Content-Type does not match" in {
    val i = Input.post("/").withBody[Application.Xml](Buf.Utf8("foo"))
    val b = body[Foo, Text.Plain :+: Application.Csv :+: CNil]
    inside(b(i).valueAttempt.unsafeRunSync()) { case Left(error) =>
      error shouldBe a[Error.NotParsed]
      error.getCause shouldBe Decode.UnsupportedMediaTypeException
    }
  }
}
