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

import com.twitter.finagle.httpx
import com.twitter.finagle.httpx.Method
import com.twitter.finagle.Service
import com.twitter.util.{Await, Return}

import io.finch._
import io.finch.request.{DecodeRequest, param}
import io.finch.response._
import io.finch.route.tokens._

import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.prop.Checkers
import shapeless.{:+:, ::, CNil, HNil, Inl}

class RouterSpec extends FlatSpec with Matchers with Checkers {

  val route = List(
    MethodToken(Method.Get), PathToken("a"), PathToken("1"), PathToken("b"), PathToken("2")
  )

  val emptyRoute = List(MethodToken(Method.Get))

  "A Router" should "extract single string" in {
    val r: Router[String] = Get / string
    r(route) shouldBe Some((route.drop(2), "a"))
  }

  it should "extract multiple strings" in {
    val r: Router2[String, String] = Get / string / "1" / string
    r(route) shouldBe Some((route.drop(4), "a" :: "b" :: HNil))
  }

  it should "match method" in {
    Get.exec(route) shouldBe Some(route.tail)
  }

  it should "match method only once" in {
    val r: Router0 = Get / Get
    r(route) shouldBe None
  }

  it should "match string" in {
    val r: Router0 = Get / "a"
    r.exec(route) shouldBe Some(route.drop(2))
  }

  it should "match 2 or more strings" in {
    val r: Router0 = Get / "a" / 1 / "b"
    r.exec(route) shouldBe Some(route.drop(4))
  }

  it should "not match if one of the routers failed" in {
    val r: Router0 = Get / "a" / 2
    r(route) shouldBe None
  }

  it should "not match if the method is missing" in {
    val r: Router0 = "a" / "b"
    r(route) shouldBe None
  }

  it should "be able to skip route tokens" in {
    val r: Router0 = * / "a"
    r.exec(route) shouldBe Some(route.drop(2))
  }

  it should "match either one or other matcher" in {
    val r: Router0 = (Get | Post) / ("a" | "b")
    r.exec(route) shouldBe Some(route.drop(2))
  }

  it should "match int" in {
    val r: Router0 = Get / "a" / 1
    r.exec(route) shouldBe Some(route.drop(3))
  }

  it should "be able to not match int if it's a different value" in {
    val r: Router0 = Get / "a" / 2
    r(route) shouldBe None
  }

  it should "be able to skip one route tokens" in {
    val r: Router0 = Get / *
    r.exec(route) shouldBe Some(route.drop(2))
  }

  it should "be able to match the whole route" in {
    val r1: Router0 = Get / *
    val r2: Router0 = Get / * / * / * / *
    r1.exec(route) shouldBe Some(route.drop(2))
    r2.exec(route) shouldBe Some(Nil)
  }

  it should "support DSL for string and int extractors" in {
    val r1: Router2[Int, String] = Get / "a" / int / string
    val r2: Router2[Int, Int] = Get / "a" / int("1") / "b" / int("2")

    r1(route) shouldBe Some((route.drop(4), 1 :: "b" :: HNil))
    r2(route) shouldBe Some((Nil, 1 :: 2 :: HNil))
  }

  it should "support DSL for boolean marchers and extractors" in {
    val route = List(PathToken("flag"), PathToken("true"))
    val r1: Router[Boolean] = "flag" / boolean
    val r2: Router0 = "flag" / true

    r1(route) shouldBe Some((Nil, true))
    r2.exec(route) shouldBe Some(Nil)
  }

  it should "be implicitly converted into a service" in {
    def echo(s: String) = new Service[HttpRequest, String] {
      def apply(request: HttpRequest) = s.toFuture
    }

    val service: Service[HttpRequest, String] = Get / string /> echo
    Await.result(service(httpx.Request("/foo"))) shouldBe "foo"
  }

  it should "be composable as an endpoint" in {
    val r1: Router[Int] = Get / "a" / int /> { _ + 10 }
    val r2: Router[Int] = Get / "b" / int / int /> { _ + _ }
    val r3: Router[Int] = r1 | r2

    r3(route) shouldBe Some((route.drop(3), 11))
  }

  it should "maps to value" in {
    val r: Router[Int] = Get /> 10
    r(route) shouldBe Some((route.tail, 10))
  }

  it should "skip all the route tokens" in {
    val r: Router0 = Get / "a" / **
    r.exec(route) shouldBe Some(Nil)
  }

  it should "converts into a string" in {
    val r1: Router0 = Get
    val r2: Router0 = Get / "a" / true / 1
    val r3: Router3[Int, Long, String] = Get / ("a" | "b") / int / long / string
    val r4: Router3[String, Int, Boolean] = Get / string("name") / int("id") / boolean("flag") / "foo"
    val r5: Router0 = Post / *

    r1.toString shouldBe "GET"
    r2.toString shouldBe "GET/a/true/1"
    r3.toString shouldBe "GET/(a|b)/:int/:long/:string"
    r4.toString shouldBe "GET/:name/:id/:flag/foo"
    r5.toString shouldBe "POST/*"
  }

  it should "support the for-comprehension syntax" in {
    val r1: Router[String] = for { a :: b :: HNil <- Get / string / int } yield a + b
    val r2: Router[String] = for { a :: b :: c :: HNil <- Get / "a" / int / string / int } yield b + c + a
    val r3: Router[String] = r1 | r2

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

    val r1: Router0 = "a" | "b" | ("a" / 10)
    val r2: Router0 = ("a" / 10) | "b" |  "a"

    r1.exec(a) shouldBe Some(Nil)
    r1.exec(b) shouldBe Some(Nil)
    r2.exec(a) shouldBe Some(Nil)
    r2.exec(b) shouldBe Some(Nil)
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
    val r1: Router0 = Get / * / * / **
    val r2: Router3[Int, String, Boolean] = Get / int / string / boolean
    val r3: Router0 = Get / "a" / "b" / "c"
    val r4: Router0 = Post

    r1.exec(emptyRoute) shouldBe Some(Nil)
    r2.exec(emptyRoute) shouldBe None
    r3.exec(emptyRoute) shouldBe None
    r4.exec(emptyRoute) shouldBe None
  }

  it should "use the first router if both eat the same number of tokens" in {
    val r: Router[String]=
      Get /> "root" |
      Get / "foo" /> "foo"

    val route1 = List(MethodToken(Method.Get))
    val route2 = List(MethodToken(Method.Get), PathToken("foo"))

    r(route1) shouldBe Some((Nil, "root"))
    r(route2) shouldBe Some((Nil, "foo"))
  }

  it should "combine coproduct routers appropriately" in {
    val r1: Router[Int :+: String :+: CNil] = int :+: string
    val r2: Router[String :+: Long :+: CNil] = string :+: long

    val r: Router[Int :+: String :+: String :+: Long :+: CNil] = r1 :+: r2

    r(PathToken("100") :: Nil) shouldBe Some((Nil, Inl(100)))
  }

  it should "convert a coproduct router into an endpoint" in {
    case class Item(s: String)

    implicit val encodeItem: EncodeResponse[Item] =
      EncodeResponse.fromString("text/plain")(_.s)

    implicit val decodeItem: DecodeRequest[Item] =
      DecodeRequest(s => Return(Item(s)))

    val responseService: Service[HttpRequest, HttpResponse] =
      Service.const(Ok("qux").toFuture)

    val itemService: Service[HttpRequest, Item] =
      Service.const(Item("item qux").toFuture)

    val service: Service[HttpRequest, HttpResponse] = (
      // Router returning an [[HttpResponse]].
      Get / "foo" /> Ok("foo")              :+:
      // Router returning an encodeable value.
      Get / "foo" / string /> Item          :+:
      // Router returning an [[HttpResponse]] in a future.
      Get / "bar" /> Ok("foo").toFuture     :+:
      // Router returning an encodeable value in a future.
      Get / "baz" /> Item("item foo").toFuture   :+:
      // Router returning a [[RequestReader]].
      Get / "qux" /> param("p").as[Item]    :+:
      // Router returning a Finagle service returning a [[HttpResponse]].
      Get / "qux" / "s1" /> responseService :+:
      // Router returning a Finagle service returning an encodeable value.
      Get / "qux" / "s2" /> itemService
    ).toService

    val res1 = Await.result(service(httpx.Request("/foo")))
    val res2 = Await.result(service(httpx.Request("/foo/t")))
    val res3 = Await.result(service(httpx.Request("/bar")))
    val res4 = Await.result(service(httpx.Request("/baz")))
    val res5 = Await.result(service(httpx.Request("/qux?p=something")))
    val res6 = Await.result(service(httpx.Request("/qux/s1")))
    val res7 = Await.result(service(httpx.Request("/qux/s2")))

    res1.contentString shouldBe Ok("foo").contentString
    res2.contentString shouldBe Ok("t").contentString
    res3.contentString shouldBe Ok("foo").contentString
    res4.contentString shouldBe Ok("item foo").contentString
    res5.contentString shouldBe Ok("something").contentString
    res6.contentString shouldBe Ok("qux").contentString
    res7.contentString shouldBe Ok("item qux").contentString
  }

  it should "convert a value router into an endpoint" in {
    val s: Service[HttpRequest, HttpResponse] = (Get / "foo" /> "bar").toService

    Await.result(s(httpx.Request("/foo"))).contentString shouldBe Ok("bar").contentString
  }

  "A string matcher" should "have the correct string representation" in {
    check { (s: String) =>
      val matcher: Router[HNil] = s

      matcher.toString === s
    }
  }

  "A router disjunction" should "have the correct string representation" in {
    check { (s: String, t: String) =>
      val router: Router[HNil] = s | t

      router.toString === s"($s|$t)"
    }
  }

  "A mapped router" should "have the correct string representation" in {
    check { (s: String, i: Int) =>
      val matcher: Router[HNil] = s
      val router: Router[Int] = matcher.map(_ => i)

      router.toString === s
    }
  }

  "A flatMapped router" should "have the correct string representation" in {
    check { (s: String) =>
      val matcher: Router[HNil] = s
      val router: Router[String] = matcher.flatMap(_ => string)

      router.toString === s
    }
  }

  "An embedFlatMapped router" should "have the correct string representation" in {
    check { (s: String, t: Option[String]) =>
      val matcher: Router[HNil] = s
      val router: Router[String] = matcher.embedFlatMap(_ => t)

      router.toString === s
    }
  }

  "An coproduct router with two elements" should "have the correct string representation" in {
    val router: Router[String :+: Int :+: CNil] = string :+: int

    assert(router.toString === "(:string|:int)")
  }

  "An coproduct router with more than two elements" should "have the correct string representation" in {
    val router: Router[String :+: Int :+: Long :+: CNil] = string :+: int :+: long

    assert(router.toString === "(:string|(:int|:long))")
  }
}
