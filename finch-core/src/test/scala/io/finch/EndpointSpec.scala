package io.finch

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.http.{Request, Method, Status}
import com.twitter.finagle.http.path._
import com.twitter.util.{Future, Await}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class EndpointSpec extends FlatSpec {

  def mockService(response: String) = new Service[HttpRequest, String] {
    def apply(req: HttpRequest) = response.toFuture
  }

  def mockRequest(uri: String) = Request(uri)

  def mockEndpoint(fromTo: (String, String)) = new Endpoint[HttpRequest, String] {
    val (from, to) = fromTo
    def route = {
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
      new Service[HttpRequest, Int] {
        def apply(req: HttpRequest) = 42.toFuture
      }
    }

    Await.result(pipeEndpoint(mockRequest("a"))) shouldBe "b"
    Await.result(andThenEndpoint(mockRequest("a"))) shouldBe 42
  }

  it should "be composable with Filter" in {
    val endpoint = mockEndpoint("a" -> "a")
    val filter = new Filter[HttpRequest, Int, HttpRequest, String] {
      def apply(req: HttpRequest, service: Service[HttpRequest, String]) =
        service(req) map { _ => 42 }
    }
    val filterEndpoint = filter ! endpoint

    Await.result(filterEndpoint(mockRequest("a"))) shouldBe 42
  }

  it should "be convertible to Service" in {
    val endpoint = mockEndpoint("a" -> "a")
    val service = endpoint.toService

    Await.result(service(mockRequest("a"))) shouldBe "a"
  }
}
