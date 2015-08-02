package io.finch

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.httpx.{Request, Method, Status}
import com.twitter.finagle.httpx.path._
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class EndpointSpec extends FlatSpec with Matchers {

  def mockService(response: String) = new Service[Request, String] {
    def apply(req: Request) = response.toFuture
  }

  def mockRequest(uri: String) = Request(uri)

  def mockEndpoint(fromTo: (String, String)) = {
    val (from, to) = fromTo

    Endpoint {
      case Method.Get -> Root / `from` => mockService(to)
    }
  }

  "An Endpoint" should "route the requests" in {
    val endpoint = mockEndpoint("a" -> "a") orElse mockEndpoint("b" -> "b")
    Await.result(endpoint(mockRequest("a"))) shouldBe "a"
    Await.result(endpoint(mockRequest("b"))) shouldBe "b"
  }

  it should "support `NotFound` route" in {
    val notFound = Endpoint.NotFound
    Await.result(notFound(mockRequest(""))).status shouldBe Status.NotFound
  }

  it should "be composable with other Endpoint" in {
    val endpoint = Endpoint.join(
      mockEndpoint("a" -> "a"),
      mockEndpoint("b" -> "b")
    )
    Await.result(endpoint(mockRequest("a"))) shouldBe "a"
    Await.result(endpoint(mockRequest("b"))) shouldBe "b"
  }

  it should "be composable with Service" in {
    val endpoint = mockEndpoint("a" -> "a")
    val service = new Service[String, String] {
      def apply(req: String) = "b".toFuture
    }
    val pipeEndpoint = endpoint ! service
    val andThenEndpoint = endpoint andThen { underlying =>
      new Service[Request, Int] {
        def apply(req: Request) = 42.toFuture
      }
    }

    Await.result(pipeEndpoint(mockRequest("a"))) shouldBe "b"
    Await.result(andThenEndpoint(mockRequest("a"))) shouldBe 42
  }

  it should "be composable with Filter" in {
    val endpoint = mockEndpoint("a" -> "a")
    val filter = new Filter[Request, Int, Request, String] {
      def apply(req: Request, service: Service[Request, String]) =
        service(req) map { _ => 42 }
    }
    val filterEndpoint = filter ! endpoint

    Await.result(filterEndpoint(mockRequest("a"))) shouldBe 42
  }

  it should "be convertible to Service" in {
    val endpoint = mockEndpoint("a" -> "a")
    Await.result(endpoint(mockRequest("a"))) shouldBe "a"
  }

  it should "allow for endpoint creation from futures" in {
    val endpoint: Endpoint[Request, String] = Endpoint {
      case Method.Get -> Root / "a" => "a".toFuture
    }
    val service: Service[Request, String] = endpoint
    Await.result(service(mockRequest("a"))) shouldBe "a"
  }

  it should "convert to service using the implicits" in {
    case class Req(r: Request)
    implicit val reqEv = (req: Req) => req.r
    val endpoint = Endpoint[Req, String] {
      case Method.Get -> Root / "a" => "a".toFuture
    }

    val service: Service[Req, String] = endpoint
    Await.result(service(Req(mockRequest("a")))) shouldBe "a"
  }
}
