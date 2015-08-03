package io.finch

import com.twitter.finagle.{SimpleFilter, Service}
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class FilterOpsSpec extends FlatSpec with Matchers {

  private[finch] class PrefixFilter(val prefix: String) extends SimpleFilter[Request, String] {
    def apply(req: Request, service: Service[Request, String]): Future[String] = {
      service(req) map { rep => prefix ++ rep }
    }
  }

  val bar = Service.mk { (_: Request) => Future.value("bar") }
  val req = Request("/")

  "FilterOps" should "allow for chaining a filter to a service" in {
    val foo = new PrefixFilter("foo")
    val combined = foo ! bar

    Await.result(combined(req)) shouldBe "foobar"
  }

  it should "allow for chaining filters to filters" in {
    val fo = new PrefixFilter("fo")
    val oo = new PrefixFilter("oo")
    val combined = fo ! oo ! bar

    Await.result(combined(req)) shouldBe "fooobar"
  }
}

