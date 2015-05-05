package io.finch.demo

import java.net.InetSocketAddress
import java.nio.charset.Charset

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Request
import com.twitter.util.Await
import io.finch._
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, HttpMethod, HttpVersion}
import org.scalatest._

class DemoSpec extends FlatSpec with Matchers {

  val await = mkAwait(Demo.backend)

  "The demo" should "deny unauthorized requests" in {
    await(Request()).status shouldBe io.finch.response.Unauthorized().status
  }

  it should "allow authorized requests for /users" in {
    val req = GET("/users")

    await(req).status shouldBe io.finch.response.Ok().status
  }

  it should "allow posting authorized requests" in {
    val req = POST("/users?name=foobar")

    await(req).status shouldBe io.finch.response.Ok().status
  }

  it should "allow posting authorized ticket requests" in {
    val req = POST("/users/0/tickets", """{"label": "foobar"}""")

    await(req).status shouldBe io.finch.response.Ok().status
  }

  it should "trigger BadRequest when user not found" in {
    val req = GET("/users/9879")

    await(req).status shouldBe io.finch.response.BadRequest.status
  }

  it should "raise NotFound for routes not found" in {
    val req = GET("/foo/bar")

    await(req).status shouldBe io.finch.response.NotFound.status
  }

  it should "raise BadRequest for params not present" in {
    val req = POST("/users/1/tickets")

    await(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "trigger BadRequest for json item not present" in {
    val req = POST("/users/0/tickets", """{"foo": "bar"}""")

    await(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "raise BadRequest for body not present" in {
    val req = POST("/users")

    await(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "raise BadRequest for invalid JSON body" in {
    val req = POST("/users/2/tickets", "{foobar")

    await(req).status shouldBe io.finch.response.BadRequest().status
  }

  it should "raise BadRequest for invalid parameters" in {
    val req = POST("/users?name=x")

    await(req).status shouldBe io.finch.response.BadRequest().status
  }

  def mkAwait(service: Service[HttpRequest, HttpResponse]): Request => HttpResponse =
    (req) => Await.result(service(req))

  def POST(path: String, body: String): Request = POST(path, Option(body))

  def POST(path: String): Request = POST(path, None)

  def POST(path: String, body: Option[String]): Request = {
    val r = mkSecretHttpRequest(HttpMethod.POST, path)

    body.foreach { b =>
      val buf = ChannelBuffers.copiedBuffer(b, Charset.defaultCharset())
      r.setContent(buf)
      r.headers().add("content-length", buf.readableBytes())
    }

    request(r)
  }

  def GET(path: String): Request = {
    val r = mkSecretHttpRequest(HttpMethod.GET, path)
    request(r)
  }

  def request(r: DefaultHttpRequest): Request =
    new Request {
      val httpRequest = r
      lazy val remoteSocketAddress = new InetSocketAddress(0)
    }

  def mkSecretHttpRequest(m: HttpMethod, p: String): DefaultHttpRequest = {
    val r = new DefaultHttpRequest(HttpVersion.HTTP_1_1, m, p)
    r.headers().add("X-Secret", Demo.Secret)
    r
  }

}
