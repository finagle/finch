package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Return}
import io.finch.request.{DecodeRequest, param}
import io.finch.response.EncodeResponse

class EndToEndSpec extends FinchSpec {

  "A Coproduct Endpoint" should "be convertible into a Service" in {
    case class Foo(s: String)

    implicit val encodeFoo: EncodeResponse[Foo] =
      EncodeResponse.fromString("text/plain")(_.s)

    implicit val decodeFoo: DecodeRequest[Foo] =
      DecodeRequest(s => Return(Foo(s)))

    val service: Service[Request, Response] = (
      get("foo" / string) { s: String => Ok(Foo(s)) } :+:
      get("bar") { NoContent("bar") } :+:
      get("qux" ? param("foo").as[Foo]) { f: Foo => Created(f) }
    ).toService

    val rep1 = Await.result(service(Request("/foo/bar")))
    rep1.contentString shouldBe "bar"
    rep1.status shouldBe Status.Ok

    val rep2 = Await.result(service(Request("/bar")))
    rep2.contentString shouldBe "bar"
    rep2.status shouldBe Status.NoContent

    val rep3 = Await.result(service(Request("/qux?foo=something")))
    rep3.contentString shouldBe "something"
    rep3.status shouldBe Status.Created
  }

  "A Value Endpoint" should "be convertible into a Servic" in {
    val e: Endpoint[String] = get("foo") { Created("bar") }
    val s: Service[Request, Response] = e.toService

    val rep = Await.result(s(Request("/foo")))
    rep.contentString shouldBe "bar"
    rep.status shouldBe Status.Created
  }
}
