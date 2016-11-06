package io.finch

import cats.Show
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.{Await, Return}
import io.finch.internal.BufText

class EndToEndSpec extends FinchSpec {

  behavior of "Finch"

  it should "convert coproduct Endpoints into Services" in {
    case class Foo(s: String)
    object Foo {
      implicit val showFoo: Show[Foo] = Show.show(_.s)
    }

    implicit val decodeFoo: DecodeEntity[Foo] =
      DecodeEntity.instance(s => Return(Foo(s)))

    implicit val encodeException: Encode.Text[Exception] =
      Encode.text((_, cs) => BufText("ERR!", cs))

    val service: Service[Request, Response] = (
      get("foo" :: string) { s: String => Ok(Foo(s)) } :+:
      get("bar") { Created("bar") } :+:
      get("baz") { BadRequest(new IllegalArgumentException("foo")): Output[Unit] } :+:
      get("qux" :: param("foo").as[Foo]) { f: Foo => Created(f) }
    ).toServiceAs[Text.Plain]

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

  it should "convert value Endpoints into Services" in {
    val e: Endpoint[String] = get("foo") { Created("bar") }
    val s: Service[Request, Response] = e.toServiceAs[Text.Plain]

    val rep = Await.result(s(Request("/foo")))
    rep.contentString shouldBe "bar"
    rep.status shouldBe Status.Created
  }
}
