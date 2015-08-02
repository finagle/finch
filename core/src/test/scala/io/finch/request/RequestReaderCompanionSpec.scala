package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import io.finch._
import org.scalatest.{Matchers, FlatSpec}
import items._

class RequestReaderCompanionSpec extends FlatSpec with Matchers {

  "The RequestReaderCompanion" should "support a factory method based on a function that reads from the request" in {
    val request: Request = Request(("foo", "5"))
    val futureResult: Future[Option[String]] = RequestReader[Request, Option[String]](_ => Some("5"))(request)
    Await.result(futureResult) shouldBe Some("5")
  }

  it should "support a factory method based on a constant Future" in {
    val request: Request = Request(("foo", ""))
    val futureResult: Future[Int] = RequestReader.const(1.toFuture)(request)
    Await.result(futureResult) shouldBe 1
  }
  
  it should "support a factory method based on a constant value" in {
    val request: Request = Request(("foo", ""))
    val futureResult: Future[Int] = RequestReader.value(1)(request)
    Await.result(futureResult) shouldBe 1
  }
  
  it should "support a factory method based on a constant exception" in {
    val request: Request = Request(("foo", ""))
    val futureResult: Future[Int] = RequestReader.exception(NotPresent(BodyItem))(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }
  
}
