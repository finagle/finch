package io.finch.response

import com.twitter.finagle.http.Status
import io.finch.json.JsonObject
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class RespondSpec extends FlatSpec {

  "A Respond" should "have the status code that it is set with" in {
    val str = "Some Content!"
    val respond = Respond(Status.Ok)(str)
    respond.status shouldBe Status.Ok
  }

  it should "set plain test as its content string" in {
    val str = "Some Content!"
    val respond = Respond(Status.Ok)(str)
    respond.getContentString() shouldBe str
  }

  it should "only include that headers that are set on it" in {
    val respond = Ok.withHeaders(("Location", "/somewhere"))()
    respond.headerMap shouldBe Map("Location" -> "/somewhere")
  }

  it should "set json as the content string" in {
    val json = JsonObject.empty
    val respond = Ok(json)
    respond.contentString = json.toString()
  }
}