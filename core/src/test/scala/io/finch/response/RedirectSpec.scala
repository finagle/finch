package io.finch.response

import com.twitter.finagle.httpx.{Request, Status}
import com.twitter.finagle.httpx.path.Root
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class RedirectSpec extends FlatSpec with Matchers {

  "A Redirect" should "create a service from a string url that generates a redirect" in {
    val redirect = Redirect("/some/route")
    val request = Request()
    val futureResponse = redirect(request)
    val response = Await.result(futureResponse)

    response.status shouldBe Status.SeeOther
    response.headerMap shouldBe Map("Location" -> "/some/route")
  }

  it should "create a service from a path that generates a redirect" in {
    val redirect = Redirect(Root / "some" / "route")
    val request = Request()
    val futureResponse = redirect(request)
    val response = Await.result(futureResponse)

    response.status shouldBe Status.SeeOther
    response.headerMap shouldBe Map("Location" -> "/some/route")
  }
}
