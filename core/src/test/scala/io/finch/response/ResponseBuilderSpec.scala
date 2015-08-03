package io.finch.response

import com.twitter.finagle.httpx.{Status, Cookie}
import com.twitter.io.Buf
import com.twitter.io.Buf.{Utf8, ByteBuffer}
import org.scalatest.{Matchers, FlatSpec}

class ResponseBuilderSpec extends FlatSpec with Matchers {

  "A ResponseBuilder" should "have the status code that it is set with" in {
    val str = "Some Content!"
    val rep = ResponseBuilder(Status.Ok)(str)
    rep.status shouldBe Status.Ok
  }

  it should "set plain text as its content string" in {
    val str = "Some Content!"
    val rep = ResponseBuilder(Status.Ok)(str)
    rep.getContentString() shouldBe str
    rep.mediaType shouldBe Some("text/plain")
  }

  it should "only include that headers that are set on it" in {
    val rep = Ok.withHeaders("Location" -> "/somewhere")()
    rep.headerMap shouldBe Map("Location" -> "/somewhere")
  }

  it should "build empty responses with status" in {
    val rep = SeeOther()
    rep.getContentString() shouldBe ""
    rep.status shouldBe Status.SeeOther
  }

  it should "include cookies that are set on it" in {
    val cookie = new Cookie("session", "random-string")
    val rep = Ok.withCookies(cookie)
    val response = rep()

    rep.cookies shouldBe Seq(cookie)
    response.cookies.get("session") shouldBe Some(cookie)
  }

  it should "override the contentType" in {
    val ok = Ok.withContentType(Some("application/json"))
    // text/plain is provided but application/json should be used
    val rep = ok("foo")

    rep.contentType shouldBe Some("application/json;charset=utf-8")
  }

  it should "set the charset" in {
    val rep = Ok.withCharset(Some("utf-7"))("charset")

    rep.contentType shouldBe Some("text/plain;charset=utf-7")
  }

  it should "override the charset" in {
    val rep = Ok.withCharset(Some("utf-7")).withContentType(Some("text/html")).withCharset(Some("utf-16"))("foo")

    rep.contentType shouldBe Some("text/html;charset=utf-16")
  }

  it should "return invalid UTF-8 bytes as is" in {
    val rep = Ok(Buf.ByteArray.Shared(Array[Byte](0x78, 0xFF.toByte)))

    Buf.ByteArray.Shared.extract(rep.content) shouldBe Array[Byte](0x78, 0xFF.toByte)
  }

  it should "not set charset if explicitly specified None" in {
    val rep = Ok.withContentType(Some("application/octet-stream")).withCharset(None)(Utf8("finch"))

    rep.contentType shouldBe Some("application/octet-stream")
  }
}
