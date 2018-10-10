package io.finch

import cats.effect.IO
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.Await
import io.finch.data.Foo
import shapeless._

class EndToEndSpec extends FinchSpec {

  behavior of "Finch"

  type AllContentTypes = Application.Json :+: Application.AtomXml :+: Application.Csv :+:
    Application.Javascript :+: Application.OctetStream :+: Application.RssXml :+:
    Application.WwwFormUrlencoded :+: Application.Xml :+: Text.Plain :+: Text.Html :+: Text.EventStream :+: CNil

  private implicit def encodeHNil[CT <: String]: Encode.Aux[HNil, CT] = Encode.instance((_, _) => Buf.Utf8("hnil"))

  private val allContentTypes = Seq(
    "application/json",
    "application/atom+xml",
    "application/csv",
    "application/javascript",
    "application/octet-stream",
    "application/rss+xml",
    "application/x-www-form-urlencoded",
    "application/xml",
    "text/plain",
    "text/html",
    "text/event-stream"
  )

  it should "convert coproduct Endpoints into Services" in {
    implicit val encodeException: Encode.Text[Exception] =
      Encode.text((_, cs) => Buf.ByteArray.Owned("ERR!".getBytes(cs.name)))

    val service: Service[Request, Response] = (
      get("foo" :: path[String]) { s: String => Ok(Foo(s)) } :+:
      get("bar") { Created("bar") } :+:
      get("baz") { BadRequest(new IllegalArgumentException("foo")): Output[Unit] } :+:
      get("qux" :: param[Foo]("foo")) { f: Foo => Created(f) }
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
    val e: Endpoint[IO, String] = get("foo") { Created("bar") }
    val s: Service[Request, Response] = e.toServiceAs[Text.Plain]

    val rep = Await.result(s(Request("/foo")))
    rep.contentString shouldBe "bar"
    rep.status shouldBe Status.Created
  }

  it should "ignore Accept header when negotiation is not enabled" in {
    check { req: Request =>
      val s = Bootstrap.serve[AllContentTypes](pathAny).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("text/event-stream")
    }
  }

  it should "ignore Accept header when single type is used for serve" in {
    check { req: Request =>
      val s = Bootstrap.serve[Text.Plain](pathAny).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("text/plain")
    }
  }

  it should "respect Accept header when coproduct type is used for serve" in {
    check { req: Request =>
      val s = Bootstrap.serve[AllContentTypes](pathAny).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === req.accept.headOption
    }
  }

  it should "ignore order of values in Accept header and use first appropriate encoder in coproduct" in {
    check { (req: Request, accept: Accept) =>
      val a = s"${accept.primary}/${accept.sub}"
      req.accept = a +: req.accept

      val s = Bootstrap.serve[AllContentTypes](pathAny).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      val first = allContentTypes.collectFirst {
        case ct if req.accept.contains(ct) => ct
      }

      rep.contentType === first
    }
  }

  it should "select last encoder when Accept header is missing/empty" in {
    check { req: Request =>
      req.headerMap.remove(Fields.Accept)
      val s = Bootstrap.serve[AllContentTypes](pathAny).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("text/event-stream")
    }
  }

  it should "select last encoder when Accept header value doesn't match any existing encoder" in {
    check { (req: Request, accept: Accept) =>
      req.accept = s"${accept.primary}/foo"
      val s = Bootstrap.serve[AllContentTypes](pathAny).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("text/event-stream")
    }
  }
}
