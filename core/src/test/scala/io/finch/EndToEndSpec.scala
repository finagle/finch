package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Return}

class EndToEndSpec extends FinchSpec {

  "A Coproduct Endpoint" should "be convertible into a Service" in {
    case class Foo(s: String)

    implicit val encodeFoo: EncodeResponse[Foo] =
      EncodeResponse.fromString("text/plain")(_.s)

    implicit val decodeFoo: DecodeRequest[Foo] =
      DecodeRequest.instance(s => Return(Foo(s)))

    implicit val encodeException: EncodeResponse[Exception] =
      EncodeResponse.fromString("text/plain")(_ => "ERR!")

    val service: Service[Request, Response] = (
      get("foo" / string) { s: String => Ok(Foo(s)) } :+:
      get("bar") { Created("bar") } :+:
      get("baz") { BadRequest(new IllegalArgumentException("foo")): Output[Unit] } :+:
      get("qux" ? param("foo").as[Foo]) { f: Foo => Created(f) }
    ).toService

    val rep1 = Await.result(service(Request("/foo/bar")))
    rep1.contentString shouldBe "bar"
    rep1.status shouldBe Status.Ok

    val rep2 = Await.result(service(Request("/bar")))
    rep2.contentString shouldBe "bar"
    rep2.status shouldBe Status.Created

    val rep3 = Await.result(service(Request("/baz")))
    rep3.contentString shouldBe "ERR!"
    rep3.status shouldBe Status.BadRequest

    val rep4 = Await.result(service(Request("/qux?foo=something")))
    rep4.contentString shouldBe "something"
    rep4.status shouldBe Status.Created
  }

  "A Value Endpoint" should "be convertible into a Service" in {
    val e: Endpoint[String] = get("foo") { Created("bar") }
    val s: Service[Request, Response] = e.toService

    val rep = Await.result(s(Request("/foo")))
    rep.contentString shouldBe "bar"
    rep.status shouldBe Status.Created
  }
}
