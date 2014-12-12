/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 */

package io.finch

import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.httpx.{Request, Method, Status}
import com.twitter.finagle.httpx.path._
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class EndpointSpec extends FlatSpec with Matchers {

  def mockService(response: String) = new Service[HttpRequest, String] {
    def apply(req: HttpRequest) = response.toFuture
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

  it should "allow for endpoint creation from futures" in {
    val endpoint: Endpoint[HttpRequest, String] =
      Endpoint { case Method.Get -> Root / "a" => "a".toFuture }
    val service = endpoint.toService

    Await.result(service(mockRequest("a"))) shouldBe "a"
  }
}
