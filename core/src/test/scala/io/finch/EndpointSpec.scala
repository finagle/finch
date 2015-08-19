package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.{Await, Base64StringEncoder, Future, Return}
import io.finch.request.{DecodeRequest, RequestReader, param}
import io.finch.response._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import shapeless.{:+:, ::, CNil, HNil, Inl}

class EndpointSpec extends FlatSpec with Matchers with Checkers {
  import Endpoint._

  val route = Input(Request("/a/1/b/2"))
  val emptyRoute = Input(Request())

  def runAndAwait[A](e: Endpoint[A], input: Input = route): Option[(Input, A)] =
    e(input).map {
      case (input, result) => (input, Await.result(result()))
    }

  def runAndForget[A](e: Endpoint[A], input: Input = route): Option[Input] = e(input).map(_._1)

  "An Endpoint" should "extract single string" in {
    val r: Endpoint[String] = get(string)
    runAndAwait(r) shouldBe Some((route.drop(1), "a"))
  }

  it should "extract multiple strings" in {
    val r: Endpoint2[String, String] = get(string / "1" / string)
    runAndAwait(r) shouldBe Some((route.drop(3), "a" :: "b" :: HNil))
  }

  it should "match string" in {
    val r: Endpoint0 = get("a")
    runAndForget(r) shouldBe Some(route.drop(1))
  }

  it should "match 2 or more strings" in {
    val r: Endpoint0 = get("a" / 1 / "b")
    runAndForget(r) shouldBe Some(route.drop(3))
  }

  it should "not match if one of the endpoits failed" in {
    val r: Endpoint0 = get("a" / 2)
    runAndAwait(r) shouldBe None
  }

  it should "not match if the method is missing" in {
    val r: Endpoint0 = "a" / "b"
    runAndAwait(r) shouldBe None
  }

  it should "be able to skip route tokens" in {
    val r: Endpoint0 =  "a" / "1" / *
    runAndForget(r) shouldBe Some(route.drop(4))
  }

  it should "match either one or other matcher" in {
    val r: Endpoint0 = get("a" | "b")
    runAndForget(r) shouldBe Some(route.drop(1))
  }

  it should "match int" in {
    val r: Endpoint0 = get("a" / 1)
    runAndForget(r) shouldBe Some(route.drop(2))
  }

  it should "be able to not match int if it's a different value" in {
    val r: Endpoint0 = get("a" / 2)
    runAndAwait(r) shouldBe None
  }

  it should "be able to match method" in {
    val r: Endpoint0 = get(/)
    runAndForget(r) shouldBe Some(route)
  }

  it should "be able to match the whole route" in {
    val r1: Endpoint0 = *
    runAndForget(r1) shouldBe Some(route.drop(4))
  }

  it should "support DSL for string and int extractors" in {
    val r1: Endpoint2[Int, String] = get("a" / int / string)
    val r2: Endpoint2[Int, Int] = get("a" / int("1") / "b" / int("2"))

    runAndAwait(r1) shouldBe Some((route.drop(3), 1 :: "b" :: HNil))
    runAndAwait(r2) shouldBe Some((route.drop(4), 1 :: 2 :: HNil))
  }

  it should "support DSL for boolean marchers and extractors" in {
    val route = Input(Request("/flag/true"))
    val r1: Endpoint[Boolean] = "flag" / boolean
    val r2: Endpoint0 = "flag" / true

    runAndAwait(r1, route) shouldBe Some((route.drop(2), true))
    runAndForget(r2, route) shouldBe Some(route.drop(2))
  }

  it should "be composable as an endpoint" in {
    val r1: Endpoint[Int] = get("a" / int /> { _ + 10 })
    val r2: Endpoint[Int] = get("b" / int / int /> { _ + _ })
    val r3: Endpoint[Int] = r1 | r2

    runAndAwait(r3) shouldBe Some((route.drop(2), 11))
  }

  it should "maps to value" in {
    val r: Endpoint[Int] = get(*) /> 10
    runAndAwait(r) shouldBe Some((route.drop(4), 10))
  }

  it should "skip all the route tokens" in {
    val r: Endpoint0 = get("a" / *)
    runAndForget(r) shouldBe Some(route.drop(4))
  }

  it should "extract the tail of the route" in {
    val r: Endpoint[Seq[String]] = get("a" / strings)
    runAndAwait(r, route) shouldBe Some((route.drop(4), Seq("1", "b", "2")))
  }

  it should "extract ints from the tail of the route" in {
    val route1 = Input(Request("/a/1/4/42"))

    val r1: Endpoint[Seq[Int]] = get("a" / ints)
    val r2: Endpoint[Int :: Seq[Int] :: HNil] = get("a" / int("1") / ints)

    runAndAwait(r1, route1) shouldBe Some((route1.drop(4), Seq(1, 4, 42)))
    runAndAwait(r1, route) shouldBe Some((route.drop(4), Seq(1, 2)))

    runAndAwait(r2, route1) shouldBe Some((route1.drop(4), 1 :: Seq(4, 42) :: HNil))
    runAndAwait(r2, route) shouldBe Some((route.drop(4), 1 :: Seq(2) :: HNil))
  }

  it should "extract booleans from the tail of the route" in {
    val route = Input(Request("/flag/true/false/true"))
    val r: Endpoint[Seq[Boolean]] = get("flag" / booleans)
    runAndAwait(r, route) shouldBe Some((route.drop(4), Seq(true, false, true)))
  }

  it should "extract the tail of the route in case it's empty" in {
    val route = Input(Request("/a"))
    val r: Endpoint[Seq[String]] = get("a" / strings)
    runAndAwait(r, route) shouldBe Some((route.drop(4), Nil))
  }

  it should "converts into a string" in {
    val r1: Endpoint0 = get(/)
    val r2: Endpoint0 = get("a" / true / 1)
    val r3: Endpoint3[Int, Long, String] = get(("a" | "b") / int / long / string)
    val r4: Endpoint3[String, Int, Boolean] = get(string("name") / int("id") / boolean("flag") / "foo")
    val r5: Endpoint0 = post(*)
    val r6: Endpoint[Seq[String]] = post(strings)

    r1.toString shouldBe "GET /"
    r2.toString shouldBe "GET /a/true/1"
    r3.toString shouldBe "GET /(a|b)/:int/:long/:string"
    r4.toString shouldBe "GET /:name/:id/:flag/foo"
    r5.toString shouldBe "POST /*"
    r6.toString shouldBe "POST /:string*"
  }

  it should "support the for-comprehension syntax" in {
    val r1: Endpoint[String] = for { a :: b :: HNil <- get(string / int) } yield a + b
    val r2: Endpoint[String] = for { a :: b :: c :: HNil <- get("a" / int / string / int) } yield b + c + a
    val r3: Endpoint[String] = r1 | r2

    runAndAwait(r1) shouldBe Some((route.drop(2), "a1"))
    runAndAwait(r2) shouldBe Some((route.drop(4), "b21"))
    runAndAwait(r3) shouldBe runAndAwait(r2)
  }

  it should "be greedy" in {
    val a = Input(Request("/a/10"))
    val b = Input(Request("/a"))

    val r1: Endpoint0 = "a" | "b" | ("a" / 10)
    val r2: Endpoint0 = ("a" / 10) | "b" |  "a"

    runAndForget(r1, a) shouldBe Some(a.drop(2))
    runAndForget(r1, b) shouldBe Some(b.drop(2))
    runAndForget(r2, a) shouldBe Some(a.drop(2))
    runAndForget(r2, b) shouldBe Some(b.drop(2))
  }

  it should "not evaluate futures until matched" in {
    val a = Input(Request("/a/10"))
    var flag = false

    val routerWithFailedFuture: Endpoint0 = "a".embedFlatMap { hnil =>
      Future {
        flag = true
        hnil
      }
    }

    val router: Endpoint0 = ("a" / 10) | routerWithFailedFuture

    runAndAwait(router, a) shouldBe Some((a.drop(2), HNil))
    flag shouldBe false
  }

  it should "handle the empty route well" in {
    val r1: Endpoint0 = get(*)
    val r2: Endpoint3[Int, String, Boolean] = get(int / string / boolean)
    val r3: Endpoint0 = get("a" / "b" / "c")
    val r4: Endpoint0 = post(*)

    runAndForget(r1, emptyRoute) shouldBe Some(emptyRoute)
    runAndForget(r2, emptyRoute) shouldBe None
    runAndForget(r3, emptyRoute) shouldBe None
    runAndForget(r4, emptyRoute) shouldBe None
  }

  it should "use the first router if both eat the same number of tokens" in {
    val r: Endpoint[String]=
      get("foo") /> "foo" |
      get(/) /> "root"

    val route1 = Input(Request())
    val route2 = Input(Request("/foo"))

    runAndAwait(r, route1) shouldBe Some((route1, "root"))
    runAndAwait(r, route2) shouldBe Some((route2.drop(1), "foo"))
  }

  it should "combine coproduct routers appropriately" in {
    val r1: Endpoint[Int :+: String :+: CNil] = int :+: string
    val r2: Endpoint[String :+: Long :+: CNil] = string :+: long

    val r: Endpoint[Int :+: String :+: String :+: Long :+: CNil] = r1 :+: r2

    val route = Input(Request("/100"))
    runAndAwait(r, route) shouldBe Some((route.drop(1), Inl(100)))
  }

  it should "convert a coproduct router into an endpoint" in {
    case class Item(s: String)

    implicit val encodeItem: EncodeResponse[Item] =
      EncodeResponse.fromString("text/plain")(_.s)

    implicit val decodeItem: DecodeRequest[Item] =
      DecodeRequest(s => Return(Item(s)))

    val service: Service[Request, Response] = (
      // Router returning an encodeable value.
      get("foo" / string) { s: String => Item(s) } :+:
        // Router returning an [[Response]].
      get("foo") { Ok("foo") }             :+:
      // Router composed with [[RequestReader]].
      get("qux") ? param("p").as[Item]
    ).toService

    val res1 = Await.result(service(Request("/foo")))
    val res2 = Await.result(service(Request("/foo/t")))
    val res3 = Await.result(service(Request("/qux?p=something")))

    res1.contentString shouldBe Ok("foo").contentString
    res2.contentString shouldBe Ok("t").contentString
    res3.contentString shouldBe Ok("something").contentString
  }

  it should "convert a value router into an endpoint" in {
    val s: Service[Request, Response] = get("foo") { "bar" }.toService

    Await.result(s(Request("/foo"))).contentString shouldBe Ok("bar").contentString
  }

  it should "be composable with RequestReaders" in {
    val pagination: RequestReader[(Int, Int)] = (param("from").as[Int] :: param("to").as[Int]).asTuple
    val router: Endpoint[Int] = get("a" / int / "b" ? pagination) /> { (i, p) => i + p._1 + p._2 }
    val route = Input(Request("/a/10/b", "from" -> "100", "to" -> "200"))

    runAndAwait(router, route) shouldBe Some((route.drop(3), 310))
  }

  it should "maps with Mapper" in {
    val r1: Endpoint[Int] = Endpoint.value(100)
    val r2: Endpoint[String] = r1 { i: Int => i.toString }
    val r3: Endpoint[String] = r1 { i: Int => Future.value(i.toString) }
    val r4: Endpoint2[Int, Int] = Endpoint.value(10) / Endpoint.value(100)
    val r5: Endpoint[Int] = r4 { (a: Int, b: Int) => a + b }
    val r6: Endpoint[Int] = r4 { (a: Int, b: Int) => Future.value(a + b) }
    val r7: Endpoint0 = /
    val r8: Endpoint[String] = r7 { "foo" }
    val r9: Endpoint[String] = r7 { Future.value("foo") }


    val route = Input(Request())
    runAndAwait(r1, route) shouldBe Some((route, 100))
    runAndAwait(r2, route) shouldBe Some((route, "100"))
    runAndAwait(r3, route) shouldBe Some((route, "100"))
    runAndAwait(r4, route) shouldBe Some((route, 10 :: 100 :: HNil))
    runAndAwait(r5, route) shouldBe Some((route, 110))
    runAndAwait(r6, route) shouldBe Some((route, 110))
    runAndAwait(r7, route) shouldBe Some((route, HNil))
    runAndAwait(r8, route) shouldBe Some((route, "foo"))
    runAndAwait(r9, route) shouldBe Some((route, "foo"))
  }

  it should "maps lazily to values" in {
    var i: Int = 0
    val r1: Endpoint[Int] = get(/) { i = i + 1; i }
    val r2: Endpoint[Int] = get(/) { i = i + 1; Future.value(i) }

    val route = Input(Request())
    runAndAwait(r1, route) shouldBe Some((route, 1))
    runAndAwait(r1, route) shouldBe Some((route, 2))
    runAndAwait(r1, route) shouldBe Some((route, 3))
    runAndAwait(r2, route) shouldBe Some((route, 4))
    runAndAwait(r2, route) shouldBe Some((route, 5))
    runAndAwait(r2, route) shouldBe Some((route, 6))
  }

  "A string matcher" should "have the correct string representation" in {
    check { (s: String) =>
      val matcher: Endpoint[HNil] = s

      matcher.toString === s
    }
  }

  "An endpoint disjunction" should "have the correct string representation" in {
    check { (s: String, t: String) =>
      val router: Endpoint[HNil] = s | t

      router.toString === s"($s|$t)"
    }
  }

  "A mapped endpoint" should "have the correct string representation" in {
    check { (s: String, i: Int) =>
      val matcher: Endpoint[HNil] = s
      val router: Endpoint[Int] = matcher.map(_ => i)

      router.toString === s
    }
  }

  "An ap'd endpoint" should "have the correct string representation" in {
    check { (s: String) =>
      val matcher: Endpoint[HNil] = s
      val fr: Endpoint[HNil => String] = *.map(_ => _ => "foo")
      val router: Endpoint[String] = matcher.ap(fr)

      router.toString === s
    }
  }

  "An embedFlatMapped endpoint" should "have the correct string representation" in {
    check { (s: String, ss: String) =>
      val matcher: Endpoint[HNil] = s
      val router: Endpoint[String] = matcher.embedFlatMap(_ => Future.value(ss))

      router.toString === s
    }
  }

  "An coproduct endpoint with two elements" should "have the correct string representation" in {
    val router: Endpoint[String :+: Int :+: CNil] = string :+: int

    assert(router.toString === "(:string|:int)")
  }

  "An coproduct endpoint with more than two elements" should "have the correct string representation" in {
    val router: Endpoint[String :+: Int :+: Long :+: CNil] = string :+: int :+: long

    assert(router.toString === "(:string|(:int|:long))")
  }

  "A basicAuth combinator" should "auth the endpoint" in {
    def encode(user: String, password: String) = "Basic " + Base64StringEncoder.encode(s"$user:$password".getBytes)
    val r: Endpoint[String] = get(/) /> "foo"

    check { (u: String, p: String) =>
      val req = Request()
      req.headerMap.update("Authorization", encode(u, p))

      val rr = basicAuth(u, p)(r)
      val input = Input(req)

      rr.toString === s"BasicAuth($r)"
      runAndAwait(rr, input) === Some((input, "foo"))
    }
  }

  it should "drop the request if auth is failed" in {
    val r: Endpoint0 = basicAuth("foo", "bar")(/)
    runAndAwait(r, Input(Request())) shouldBe None
  }
}
