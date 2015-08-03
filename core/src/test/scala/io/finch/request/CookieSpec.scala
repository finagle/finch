package io.finch.request

import com.twitter.finagle.httpx.{Request, Cookie}
import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class CookieSpec extends FlatSpec with Matchers {

  val name = "session"
  val c = new Cookie(name, "some-random-value")

  "A ResponseBuilder" should "read a required cookie" in {
    val request: Request = Request()
    request.addCookie(c)
    val futureResult: Future[Cookie] = cookie(name)(request)

    Await.result(futureResult) shouldBe c
  }

  it should "throw an exception if the require cookie does not exist" in {
    val request: Request = Request()
    request.addCookie(c)
    val futureResult: Future[Cookie] = cookie("another-cookie")(request)

    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "read an optional cookie if it exists" in {
    val request: Request = Request()
    request.addCookie(c)
    val futureResult: Future[Option[Cookie]] = cookieOption(name)(request)

    Await.result(futureResult) shouldBe Some(c)
  }

  it should "read None if the cookie name does not exist" in {
    val request: Request = Request()
    val futureResult: Future[Option[Cookie]] = cookieOption(name)(request)

    Await.result(futureResult) shouldBe None
  }

  it should "have a matching RequestItem" in {
    cookie(name).item shouldBe items.CookieItem(name)
    cookieOption(name).item shouldBe items.CookieItem(name)
  }
}
