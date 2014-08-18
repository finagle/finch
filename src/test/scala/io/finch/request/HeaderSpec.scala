package io.finch.request

import com.twitter.finagle.http.Request
import com.twitter.util.Await
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class HeaderSpec extends FlatSpec {

  "A RequiredHeader" should "properly read the header field" in {
    val request = Request()
    request.headers().set("Location", "some header")
    val futureResult = RequiredHeader("Location")(request)
    Await.result(futureResult) should equal("some header")
  }

  it should "error if it does not exist" in {
    val request = Request()
    val futureResult = RequiredHeader("Location")(request)
    intercept[HeaderNotFound] {
      Await.result(futureResult)
    }
  }


  "An OptionalHeader" should "properly read an existing header field" in {
    val request = Request()
    request.headers().set("Location", "some header")
    val futureResult = OptionalHeader("Location")(request)
    Await.result(futureResult) should equal(Some("some header"))
  }

  it should "be None if it does not exist" in {
    val request = Request()
    val futureResult = OptionalHeader("Location")(request)
    Await.result(futureResult) should be (None)
  }
}