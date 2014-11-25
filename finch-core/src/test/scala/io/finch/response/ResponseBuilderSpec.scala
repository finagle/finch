package io.finch.response

import com.twitter.finagle.http.Status
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class ResponseBuilderSpec extends FlatSpec {

  "A ResponseBuilder" should "have the status code that it is set with" in {
    val str = "Some Content!"
    val rep = ResponseBuilder(Status.Ok)(str)
    rep.status shouldBe Status.Ok
  }

  it should "set plain test as its content string" in {
    val str = "Some Content!"
    val rep = ResponseBuilder(Status.Ok)(str)
    rep.getContentString() shouldBe str
  }

  it should "only include that headers that are set on it" in {
    val rep = Ok.withHeaders("Location" -> "/somewhere")()
    rep.headerMap shouldBe Map("Location" -> "/somewhere")
  }

  it should "build empty responses with status" in {
    val rep = SeeOther()
    rep.getContentString() shouldBe ""
    rep.getStatus() shouldBe Status.SeeOther
  }
}