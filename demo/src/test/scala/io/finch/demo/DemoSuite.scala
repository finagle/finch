package io.finch.demo

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Method.{Get, Post}
import com.twitter.finagle.httpx.{Method, Request, Response}
import com.twitter.io.Buf
import io.finch.test.ServiceSuite
import org.scalatest.Matchers
import org.scalatest.fixture.FlatSpec

trait DemoSuite { this: ServiceSuite with FlatSpec with Matchers =>
  def createService(): Service[Request, Response] = Demo.backend

  "The demo" should "deny unauthorized requests" in { f =>
    f(Request()).status shouldBe io.finch.response.Unauthorized().status
  }

  it should "allow authorized requests for /users" in { f =>
    val req = GET("/users")

    f(req).status shouldBe io.finch.response.Ok().status
  }

  it should "allow posting authorized requests" in { f =>
    val req = POST("/users?name=foobar")

    f(req).status shouldBe io.finch.response.Ok().status
  }

  it should "allow posting authorized ticket requests" in { f =>
    val req = POST("/users/0/tickets", "{\"label\": \"foobar\"}")

    f(req).status shouldBe io.finch.response.Ok().status
  }

  it should "trigger BadRequest when user not found" in { f =>
    val req = GET("/users/9879")

    f(req).status shouldBe io.finch.response.BadRequest.status
  }

  it should "raise NotFound for routes not found" in { f =>
    val req = GET("/foo/bar")

    f(req).status shouldBe io.finch.response.NotFound.status
  }

  it should "raise BadRequest for params not present" in { f =>
    val req = POST("/users/1/tickets")

    f(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "raise BadRequest for body not present" in { f =>
    val req = POST("/users")

    f(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "raise BadRequest for invalid JSON body" in { f =>
    val req = POST("/users/2/tickets", "{foobar")

    f(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "raise BadRequest for invalid parameters" in { f =>
    val req = POST("/users?name=x")

    f(req).status shouldBe io.finch.response.BadRequest().status
  }

  def POST(path: String, body: String): Request = POST(path, Option(body))

  def POST(path: String): Request = POST(path, None)

  def POST(path: String, body: Option[String]): Request = {
    val r = mkSecretHttpRequest(Post, path)

    body.foreach { b =>
      val buf = Buf.Utf8(b)
      r.content = buf
      r.contentLength = buf.length.toLong
    }
    r
  }

  def GET(path: String): Request = {
    mkSecretHttpRequest(Get, path)
  }

  def mkSecretHttpRequest(m: Method, p: String): Request = {
    val r = Request(m, p)
    r.headerMap.update("X-Secret", Demo.Secret)
    r
  }
}
