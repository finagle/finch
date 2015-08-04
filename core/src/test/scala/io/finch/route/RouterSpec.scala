package io.finch.route

import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.Service
import com.twitter.util.{Base64StringEncoder, Future, Await, Return}

import io.finch._
import io.finch.request.{RequestReader, DecodeRequest, param}
import io.finch.response._
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import shapeless.{:+:, ::, CNil, HNil, Inl}

class RouterSpec extends FlatSpec with Matchers with Checkers {
  import Router._

  val route = Input(Request("/a/1/b/2"))
  val emptyRoute = Input(Request())

  def runRouter[A](router: Router[A], input: Input = route): Option[(Input, A)] =
    router(input).map {
      case (input, result) => (input, Await.result(result()))
    }

  def execRouter[A](router: Router[A], input: Input = route): Option[Input] = router(input).map(_._1)

  "A Router" should "extract single string" in {
    val r: Router[String] = get(string)
    runRouter(r) shouldBe Some((route.drop(1), "a"))
  }

  it should "extract multiple strings" in {
    val r: Router2[String, String] = get(string / "1" / string)
    runRouter(r) shouldBe Some((route.drop(3), "a" :: "b" :: HNil))
  }

  it should "match string" in {
    val r: Router0 = get("a")
    execRouter(r) shouldBe Some(route.drop(1))
  }

  it should "match 2 or more strings" in {
    val r: Router0 = get("a" / 1 / "b")
    execRouter(r) shouldBe Some(route.drop(3))
  }

  it should "not match if one of the routers failed" in {
    val r: Router0 = get("a" / 2)
    runRouter(r) shouldBe None
  }

  it should "not match if the method is missing" in {
    val r: Router0 = "a" / "b"
    runRouter(r) shouldBe None
  }

  it should "be able to skip route tokens" in {
    val r: Router0 =  "a" / "1" / *
    execRouter(r) shouldBe Some(route.drop(4))
  }

  it should "match either one or other matcher" in {
    val r: Router0 = get("a" | "b")
    execRouter(r) shouldBe Some(route.drop(1))
  }

  it should "match int" in {
    val r: Router0 = get("a" / 1)
    execRouter(r) shouldBe Some(route.drop(2))
  }

  it should "be able to not match int if it's a different value" in {
    val r: Router0 = get("a" / 2)
    runRouter(r) shouldBe None
  }

  it should "be able to match method" in {
    val r: Router0 = get(/)
    execRouter(r) shouldBe Some(route)
  }

  it should "be able to match the whole route" in {
    val r1: Router0 = *
    execRouter(r1) shouldBe Some(route.drop(4))
  }

  it should "support DSL for string and int extractors" in {
    val r1: Router2[Int, String] = get("a" / int / string)
    val r2: Router2[Int, Int] = get("a" / int("1") / "b" / int("2"))

    runRouter(r1) shouldBe Some((route.drop(3), 1 :: "b" :: HNil))
    runRouter(r2) shouldBe Some((route.drop(4), 1 :: 2 :: HNil))
  }

  it should "support DSL for boolean marchers and extractors" in {
    val route = Input(Request("/flag/true"))
    val r1: Router[Boolean] = "flag" / boolean
    val r2: Router0 = "flag" / true

    runRouter(r1, route) shouldBe Some((route.drop(2), true))
    execRouter(r2, route) shouldBe Some(route.drop(2))
  }

  it should "be implicitly converted into a service" in {
    def echo(s: String) = new Service[Request, String] {
      def apply(request: Request) = s.toFuture
    }

    val service: Service[Request, String] = get(string /> echo)
    Await.result(service(Request("/foo"))) shouldBe "foo"
  }

  it should "be composable as an endpoint" in {
    val r1: Router[Int] = get("a" / int /> { _ + 10 })
    val r2: Router[Int] = get("b" / int / int /> { _ + _ })
    val r3: Router[Int] = r1 | r2

    runRouter(r3) shouldBe Some((route.drop(2), 11))
  }

  it should "maps to value" in {
    val r: Router[Int] = get(*) /> 10
    runRouter(r) shouldBe Some((route.drop(4), 10))
  }

  it should "skip all the route tokens" in {
    val r: Router0 = get("a" / *)
    execRouter(r) shouldBe Some(route.drop(4))
  }

  it should "extract the tail of the route" in {
    val r: Router[Seq[String]] = get("a" / string.*)
    runRouter(r, route) shouldBe Some((route.drop(4), Seq("1", "b", "2")))
  }

  it should "extract ints from the tail of the route" in {
    val route1 = Input(Request("/a/1/4/42"))

    val r1: Router[Seq[Int]] = get("a" / int.*)
    val r2: Router[Int :: Seq[Int] :: HNil] = get("a" / int("1") / int.*)

    runRouter(r1, route1) shouldBe Some((route1.drop(4), Seq(1, 4, 42)))
    runRouter(r1, route) shouldBe Some((route.drop(4), Seq(1, 2)))

    runRouter(r2, route1) shouldBe Some((route1.drop(4), 1 :: Seq(4, 42) :: HNil))
    runRouter(r2, route) shouldBe Some((route.drop(4), 1 :: Seq(2) :: HNil))
  }

  it should "extract booleans from the tail of the route" in {
    val route = Input(Request("/flag/true/false/true"))
    val r: Router[Seq[Boolean]] = get("flag" / boolean.*)
    runRouter(r, route) shouldBe Some((route.drop(4), Seq(true, false, true)))
  }

  it should "extract the tail of the route in case it's empty" in {
    val route = Input(Request("/a"))
    val r: Router[Seq[String]] = get("a" / string.*)
    runRouter(r, route) shouldBe Some((route.drop(4), Nil))
  }

  it should "converts into a string" in {
    val r1: Router0 = get(/)
    val r2: Router0 = get("a" / true / 1)
    val r3: Router3[Int, Long, String] = get(("a" | "b") / int / long / string)
    val r4: Router3[String, Int, Boolean] = get(string("name") / int("id") / boolean("flag") / "foo")
    val r5: Router0 = post(*)
    val r6: Router[Seq[String]] = post(string.*)

    r1.toString shouldBe "GET /"
    r2.toString shouldBe "GET /a/true/1"
    r3.toString shouldBe "GET /(a|b)/:int/:long/:string"
    r4.toString shouldBe "GET /:name/:id/:flag/foo"
    r5.toString shouldBe "POST /*"
    r6.toString shouldBe "POST /:string.*"
  }

  it should "support the for-comprehension syntax" in {
    val r1: Router[String] = for { a :: b :: HNil <- get(string / int) } yield a + b
    val r2: Router[String] = for { a :: b :: c :: HNil <- get("a" / int / string / int) } yield b + c + a
    val r3: Router[String] = r1 | r2

    runRouter(r1) shouldBe Some((route.drop(2), "a1"))
    runRouter(r2) shouldBe Some((route.drop(4), "b21"))
    runRouter(r3) shouldBe runRouter(r2)
  }

  it should "be implicitly convertible into service from future" in {
    val e: Router[Service[Request, Response]] =
      (Get / "foo" /> Ok("bar").toFuture: Router[Service[Request, Response]]) |
      (Get / "bar" /> Ok("foo").toFuture)

    Await.result(e(Request("/foo"))).contentString shouldBe "bar"
    Await.result(e(Request("/bar"))).contentString shouldBe "foo"
    a [RouteNotFound] shouldBe thrownBy(Await.result(e(Request("/baz"))))
  }

  it should "be greedy" in {
    val a = Input(Request("/a/10"))
    val b = Input(Request("/a"))

    val r1: Router0 = "a" | "b" | ("a" / 10)
    val r2: Router0 = ("a" / 10) | "b" |  "a"

    execRouter(r1, a) shouldBe Some(a.drop(2))
    execRouter(r1, b) shouldBe Some(b.drop(2))
    execRouter(r2, a) shouldBe Some(a.drop(2))
    execRouter(r2, b) shouldBe Some(b.drop(2))
  }

  it should "not evaluate futures until matched" in {
    val a = Input(Request("/a/10"))
    var flag = false

    val routerWithFailedFuture: Router0 = "a".embedFlatMap { hnil =>
      Future {
        flag = true
        hnil
      }
    }

    val router: Router0 = ("a" / 10) | routerWithFailedFuture

    runRouter(router, a) shouldBe Some((a.drop(2), HNil))
    flag shouldBe false
  }

  it should "allow mix routers that returns futures and services" in {
    val service = new Service[Request, Response] {
      def apply(req: Request) = Ok("foo").toFuture
    }
    val e: Router[Service[Request, Response]] =
      (Get / "bar" /> Ok("bar").toFuture: Router[Service[Request, Response]]) |
      (Get / "foo" /> service)

    Await.result(e(Request("/foo"))).contentString shouldBe "foo"
    Await.result(e(Request("/bar"))).contentString shouldBe "bar"
  }

  it should "convert Router[Future[_]] to both endpoint and service" in {
    val s: Service[Request, Response] = Get / "foo" /> Ok("foo").toFuture: Router[Service[Request, Response]]
    val e: Router[Service[Request, Response]] = Get / "bar" /> Ok("bar").toFuture

    Await.result(s(Request("/foo"))).contentString shouldBe "foo"
    Await.result(e(Request("/bar"))).contentString shouldBe "bar"
  }

  it should "handle the empty route well" in {
    val r1: Router0 = get(*)
    val r2: Router3[Int, String, Boolean] = get(int / string / boolean)
    val r3: Router0 = get("a" / "b" / "c")
    val r4: Router0 = post(*)

    execRouter(r1, emptyRoute) shouldBe Some(emptyRoute)
    execRouter(r2, emptyRoute) shouldBe None
    execRouter(r3, emptyRoute) shouldBe None
    execRouter(r4, emptyRoute) shouldBe None
  }

  it should "use the first router if both eat the same number of tokens" in {
    val r: Router[String]=
      get("foo") /> "foo" |
      get(/) /> "root"

    val route1 = Input(Request())
    val route2 = Input(Request("/foo"))

    runRouter(r, route1) shouldBe Some((route1, "root"))
    runRouter(r, route2) shouldBe Some((route2.drop(1), "foo"))
  }

  it should "combine coproduct routers appropriately" in {
    val r1: Router[Int :+: String :+: CNil] = int :+: string
    val r2: Router[String :+: Long :+: CNil] = string :+: long

    val r: Router[Int :+: String :+: String :+: Long :+: CNil] = r1 :+: r2

    val route = Input(Request("/100"))
    runRouter(r, route) shouldBe Some((route.drop(1), Inl(100)))
  }

  it should "convert a coproduct router into an endpoint" in {
    case class Item(s: String)

    implicit val encodeItem: EncodeResponse[Item] =
      EncodeResponse.fromString("text/plain")(_.s)

    implicit val decodeItem: DecodeRequest[Item] =
      DecodeRequest(s => Return(Item(s)))

    val responseService: Service[Request, Response] =
      Service.const(Ok("qux").toFuture)

    val itemService: Service[Request, Item] =
      Service.const(Item("item qux").toFuture)

    val service: Service[Request, Response] = (
      get("foo" / string) /> Item          :+:
        // Router returning an [[HttpResponse]].
      get("foo") /> Ok("foo")              :+:
      // Router returning an encodeable value.
      // Router returning an [[HttpResponse]] in a future.
      get("bar") />> Ok("foo").toFuture    :+:
      // Router returning an encodeable value in a future.
      get("baz") />> Item("item foo").toFuture  :+:
      // Router returning a Finagle service returning a [[HttpResponse]].
      get("qux" / "s1") /> responseService :+:
      // Router returning a Finagle service returning an encodeable value.
      get("qux" / "s2") /> itemService     :+:
      // Router composed with [[RequestReader]].
      get("qux") ? param("p").as[Item]
    ).toService

    val res1 = Await.result(service(Request("/foo")))
    val res2 = Await.result(service(Request("/foo/t")))
    val res3 = Await.result(service(Request("/bar")))
    val res4 = Await.result(service(Request("/baz")))
    val res5 = Await.result(service(Request("/qux?p=something")))
    val res6 = Await.result(service(Request("/qux/s1")))
    val res7 = Await.result(service(Request("/qux/s2")))

    res1.contentString shouldBe Ok("foo").contentString
    res2.contentString shouldBe Ok("t").contentString
    res3.contentString shouldBe Ok("foo").contentString
    res4.contentString shouldBe Ok("item foo").contentString
    res5.contentString shouldBe Ok("something").contentString
    res6.contentString shouldBe Ok("qux").contentString
    res7.contentString shouldBe Ok("item qux").contentString
  }

  it should "convert a value router into an endpoint" in {
    val s: Service[Request, Response] = (Get / "foo" /> "bar").toService

    Await.result(s(Request("/foo"))).contentString shouldBe Ok("bar").contentString
  }

  it should "be composable with RequestReaders" in {
    val pagination: RequestReader[(Int, Int)] = (param("from").as[Int] :: param("to").as[Int]).asTuple
    val router: Router[Int] = get("a" / int / "b" ? pagination) /> { (i, p) => i + p._1 + p._2 }
    val route = Input(Request("/a/10/b", "from" -> "100", "to" -> "200"))

    runRouter(router, route) shouldBe Some((route.drop(3), 310))
  }

  it should "maps with Mapper" in {
    val r1: Router[Int] = Router.value(100)
    val r2: Router[String] = r1 { i: Int => i.toString }
    val r3: Router[String] = r1 { i: Int => Future.value(i.toString) }
    val r4: Router2[Int, Int] = Router.value(10) / Router.value(100)
    val r5: Router[Int] = r4 { (a: Int, b: Int) => a + b }
    val r6: Router[Int] = r4 { (a: Int, b: Int) => Future.value(a + b) }

    val route = Input(Request())
    runRouter(r1, route) shouldBe Some((route, 100))
    runRouter(r2, route) shouldBe Some((route, "100"))
    runRouter(r3, route) shouldBe Some((route, "100"))
    runRouter(r4, route) shouldBe Some((route, 10 :: 100 :: HNil))
    runRouter(r5, route) shouldBe Some((route, 110))
    runRouter(r6, route) shouldBe Some((route, 110))
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

  "An ap'd router" should "have the correct string representation" in {
    check { (s: String) =>
      val matcher: Router[HNil] = s
      val fr: Router[HNil => String] = *.map(_ => _ => "foo")
      val router: Router[String] = matcher.ap(fr)

      router.toString === s
    }
  }

  "An embedFlatMapped router" should "have the correct string representation" in {
    check { (s: String, ss: String) =>
      val matcher: Router[HNil] = s
      val router: Router[String] = matcher.embedFlatMap(_ => Future.value(ss))

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

  "A basicAuth combinator" should "auth the router" in {
    def encode(user: String, password: String) = "Basic " + Base64StringEncoder.encode(s"$user:$password".getBytes)
    val r: Router[String] = get(/) /> "foo"

    check { (u: String, p: String) =>
      val req = Request()
      req.headerMap.update("Authorization", encode(u, p))

      val rr = basicAuth(u, p)(r)
      val input = Input(req)

      rr.toString === s"BasicAuth($r)"
      runRouter(rr, input) === Some((input, "foo"))
    }
  }

  it should "drop the request if auth is failed" in {
    val r: Router0 = basicAuth("foo", "bar")(/)
    runRouter(r, Input(Request())) shouldBe None
  }
}
