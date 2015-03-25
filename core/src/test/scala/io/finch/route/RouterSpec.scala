/*
 * Copyright 2015, by Vladimir Kostyukov and Contributors.
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

package io.finch.route

import com.twitter.util.Await

import io.finch._
import io.finch.response._
import io.finch.route.tokens._

import com.twitter.finagle.httpx
import com.twitter.finagle.httpx.Method
import com.twitter.finagle.Service
import org.scalatest.{Matchers, FlatSpec}

class RouterSpec extends FlatSpec with Matchers {

  val route = List(
    MethodToken(Method.Get), PathToken("a"), PathToken("1"), PathToken("b"), PathToken("2")
  )

  val emptyRoute = List(MethodToken(Method.Get))

  "A Router" should "extract single string" in {
    val r = Get / string
    r(route) shouldBe Some((route.drop(2), "a"))
  }

  it should "extract multiple strings" in {
    val r = Get / string / "1" / string
    r(route) shouldBe Some((route.drop(4), /("a", "b")))
  }

  it should "match method" in {
    Get(route) shouldBe Some(route.tail)
  }

  it should "match method only once" in {
    val r = Get / Get
    r(route) shouldBe None
  }

  it should "match string" in {
    val r = Get / "a"
    r(route) shouldBe Some(route.drop(2))
  }

  it should "match 2 or more strings" in {
    val r = Get / "a" / 1 / "b"
    r(route) shouldBe Some(route.drop(4))
  }

  it should "not match if one of the routers failed" in {
    val r = Get / "a" / 2
    r(route) shouldBe None
  }

  it should "not match if the method is missing" in {
    val r = "a" / "b"
    r(route) shouldBe None
  }

  it should "be able to skip route tokens" in {
    val r = * / "a"
    r(route) shouldBe Some(route.drop(2))
  }

  it should "match either one or other matcher" in {
    val r = (Get | Post) / ("a" | "b")
    r(route) shouldBe Some(route.drop(2))
  }

  it should "match int" in {
    val r = Get / "a" / 1
    r(route) shouldBe Some(route.drop(3))
  }

  it should "be able to not match int if it's a different value" in {
    val r = Get / "a" / 2
    r(route) shouldBe None
  }

  it should "be able to skip one route tokens" in {
    val r = Get / *
    r (route) shouldBe Some(route.drop(2))
  }

  it should "be able to match the whole route" in {
    val r1 = Get / *
    val r2 = Get / * / * / * / *
    r1(route) shouldBe Some(route.drop(2))
    r2(route) shouldBe Some(Nil)
  }

  it should "support DSL for string and int extractors" in {
    val r1 = Get / "a" / int / string
    val r2 = Get / "a" / int("1") / "b" / int("2")

    r1(route) shouldBe Some((route.drop(4), /(1, "b")))
    r2(route) shouldBe Some((Nil, /(1, 2)))
  }

  it should "support DSL for boolean marchers and extractors" in {
    val route = List(PathToken("flag"), PathToken("true"))
    val r1 = "flag" / boolean
    val r2 = "flag" / true

    r1(route) shouldBe Some((Nil, true))
    r2(route) shouldBe Some(Nil)
  }

  it should "be implicitly converted into a service" in {
    def echo(s: String) = new Service[HttpRequest, String] {
      def apply(request: HttpRequest) = s.toFuture
    }

    val service: Service[HttpRequest, String] = Get / string /> echo
    Await.result(service(httpx.Request("/foo"))) shouldBe "foo"
  }

  it should "be composable as an endpoint" in {
    val r1 = Get / "a" / int /> { _ + 10 }
    val r2 = Get / "b" / int / int /> { _ + _ }
    val r3 = r1 | r2

    r3(route) shouldBe Some((route.drop(3), 11))
  }

  it should "maps to value" in {
    val r = Get /> 10
    r(route) shouldBe Some((route.tail, 10))
  }

  it should "skip all the route tokens" in {
    val r = Get / "a" / **
    r(route) shouldBe Some(Nil)
  }

  it should "converts into a string" in {
    val r1 = Get
    val r2 = Get / "a" / true / 1
    val r3 = Get / ("a" | "b") / int / long / string
    val r4 = Get / string("name") / int("id") / boolean("flag") / "foo"
    val r5 = Post / *

    r1.toString shouldBe "GET"
    r2.toString shouldBe "GET/a/true/1"
    r3.toString shouldBe "GET/(a|b)/:int/:long/:string"
    r4.toString shouldBe "GET/:name/:id/:flag/foo"
    r5.toString shouldBe "POST/*"
  }

  it should "support the for-comprehension syntax" in {
    val r1 = for { a / b <- Get / string / int } yield a + b
    val r2 = for { a / b / c <- Get / "a" / int / string / int } yield b + c + a
    val r3 = r1 | r2

    r1(route) shouldBe Some((route.drop(3), "a1"))
    r2(route) shouldBe Some((Nil, "b21"))
    r3(route) shouldBe r2(route)
  }

  it should "be implicitly convertible into service from future" in {
    val e: Endpoint[HttpRequest, HttpResponse] =
      (Get / "foo" /> Ok("bar").toFuture: Endpoint[HttpRequest, HttpResponse]) |
      (Get / "bar" /> Ok("foo").toFuture)

    Await.result(e(httpx.Request("/foo"))).contentString shouldBe "bar"
    Await.result(e(httpx.Request("/bar"))).contentString shouldBe "foo"
    a [RouteNotFound] shouldBe thrownBy(Await.result(e(httpx.Request("/baz"))))
  }

  it should "be greedy" in {
    val a = List(PathToken("a"), PathToken("10"))
    val b = List(PathToken("a"))

    val r1 = "a" | "b" | ("a" / 10)
    val r2 = ("a" / 10) | "b" |  "a"
    val r3 = ("a" / int) | ("b" /> 30) | ("a" /> 20)
    val r4 = ("a" /> 20) | ("b" /> 30) | ("a" / int)

    r1(a) shouldBe Some(Nil)
    r1(b) shouldBe Some(Nil)
    r2(a) shouldBe Some(Nil)
    r2(b) shouldBe Some(Nil)
    r3(a) shouldBe Some((Nil, 10))
    r3(b) shouldBe Some((Nil, 20))
    r4(a) shouldBe Some((Nil, 10))
    r4(b) shouldBe Some((Nil, 20))
  }

  it should "allow mix routers that returns futures and services" in {
    val service = new Service[HttpRequest, HttpResponse] {
      def apply(req: HttpRequest) = Ok("foo").toFuture
    }
    val e: Endpoint[HttpRequest, HttpResponse] =
      (Get / "bar" /> Ok("bar").toFuture: Endpoint[HttpRequest, HttpResponse]) |
      (Get / "foo" /> service)

    Await.result(e(httpx.Request("/foo"))).contentString shouldBe "foo"
    Await.result(e(httpx.Request("/bar"))).contentString shouldBe "bar"
  }

  it should "convert Router[Future[_]] to both endpoint and service" in {
    val s: Service[HttpRequest, HttpResponse] = Get / "foo" /> Ok("foo").toFuture: Endpoint[HttpRequest, HttpResponse]
    val e: Endpoint[HttpRequest, HttpResponse] = Get / "bar" /> Ok("bar").toFuture

    Await.result(s(httpx.Request("/foo"))).contentString shouldBe "foo"
    Await.result(e(httpx.Request("/bar"))).contentString shouldBe "bar"
  }

  it should "handle the empty route well" in {
    val r1 = Get / * / * / **
    val r2 = Get / int / string / boolean
    val r3 = Get / "a" / "b" / "c"
    val r4 = Post

    r1(emptyRoute) shouldBe Some(Nil)
    r2(emptyRoute) shouldBe None
    r3(emptyRoute) shouldBe None
    r4(emptyRoute) shouldBe None
  }

  it should "use the first router if both eats the same number of tokens" in {
    val r =
      Get /> "root" |
      Get / "foo" /> "foo"

    val route1 = List(MethodToken(Method.Get))
    val route2 = List(MethodToken(Method.Get), PathToken("foo"))

    r(route1) shouldBe Some((Nil, "root"))
    r(route2) shouldBe Some((Nil, "foo"))
  }
}
