package io.finch


import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import io.finch.response.Ok
import org.scalatest.{Matchers, FlatSpec}

class ServiceOpsSpec extends FlatSpec with Matchers {
  val foo = Service.mk { (_: Request) => Future.value("foo") }
  val bar = Service.mk {
    (req: String) => {
      Future.value(Ok(req ++ "bar"))
    }
  }
  val combined = foo ! bar

  "ServiceOps" should "allow for chaining services" in {
    val req = Request("/")
    val content = combined(req) map { r => r.getContentString }
    Await.result(content) shouldBe "foobar"
  }
}

