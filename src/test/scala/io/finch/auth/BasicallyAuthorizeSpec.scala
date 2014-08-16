package io.finch.auth

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.{Await, Base64StringEncoder, Future}
import io.finch.response.Ok
import io.finch.{HttpRequest, HttpResponse, _}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class BasicallyAuthorizeSpec extends FlatSpec {

  "A BasicallyAuthorize" should "produce an Unauthorized response if given the wrong credentials" in {
    val auth = BasicallyAuthorize("admin", "password")
    val request = Request()
    request.headers().set("Authorization", encode("wrong", "login"))
    val futureResult = auth(request, okService())
    val result = Await.result(futureResult)

    result.status shouldBe Status.Unauthorized
  }

  it should "pass the user through to the given service if the correct credentials" in {
    val auth = BasicallyAuthorize("admin", "password")
    val request = Request()
    request.headers().set("Authorization", encode("admin", "password"))
    val futureResult = auth(request, okService())
    val result = Await.result(futureResult)

    result.status shouldBe Status.Ok
  }

  private def encode(user: String, password: String) = "Basic " + Base64StringEncoder.encode(s"$user:$password".getBytes)

  private def okService() = new Service[HttpRequest, HttpResponse] {
    override def apply(request: HttpRequest): Future[HttpResponse] = Ok().toFuture
  }
}