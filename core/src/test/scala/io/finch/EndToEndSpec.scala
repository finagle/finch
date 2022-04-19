package io.finch

import cats.effect.{IO, Resource}
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

  implicit private def encodeHNil[CT <: String]: Encode.Aux[HNil, CT] = Encode.instance((_, _) => Buf.Utf8("hnil"))

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

  private def testService[E](service: Resource[IO, Service[Request, Response]])(assertions: Service[Request, Response] => E): E =
    dispatcherIO.unsafeRunSync(service.use(s => IO(assertions(s))))

  it should "convert coproduct Endpoints into Services" in {
    implicit val encodeException: Encode.Text[Exception] =
      Encode.text((_, cs) => Buf.ByteArray.Owned("ERR!".getBytes(cs.name)))

    testService(
      (
        get("foo" :: path[String]) { s: String => Ok(Foo(s)) } :+:
          get("bar")(Created("bar")) :+:
          get("baz")(BadRequest(new IllegalArgumentException("foo")): Output[Unit]) :+:
          get("qux" :: param[Foo]("foo")) { f: Foo => Created(f) }
      ).toServiceAs[Text.Plain]
    ) { service =>
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
  }

  it should "convert value Endpoints into Services" in {
    testService(get("foo")(Created("bar")).toServiceAs[Text.Plain]) { s =>
      val rep = Await.result(s(Request("/foo")))
      rep.contentString shouldBe "bar"
      rep.status shouldBe Status.Created
    }
  }

  it should "ignore Accept header when single type is used for serve" in {
    testService(Bootstrap.serve[Text.Plain](pathAny).toService) { service =>
      check { req: Request =>
        val rep = Await.result(service(req))
        rep.contentType === Some("text/plain")
      }
    }
  }

  it should "respect Accept header when coproduct type is used for serve" in {
    check { req: Request =>
      testService(Bootstrap.serve[AllContentTypes](pathAny).toService) { s =>
        val rep = Await.result(s(req))
        rep.contentType === req.accept.headOption
      }
    }
  }

  it should "ignore order of values in Accept header and use first appropriate encoder in coproduct" in {
    check { (req: Request, accept: Accept) =>
      val a = s"${accept.primary}/${accept.sub}"
      req.accept = a +: req.accept

      testService(Bootstrap.serve[AllContentTypes](pathAny).toService) { s =>
        val rep = Await.result(s(req))

        val first = allContentTypes.collectFirst {
          case ct if req.accept.contains(ct) => ct
        }

        rep.contentType === first
      }
    }
  }

  it should "select last encoder when Accept header is missing/empty" in {
    check { req: Request =>
      req.headerMap.remove(Fields.Accept)
      testService(Bootstrap.serve[AllContentTypes](pathAny).toService) { s =>
        val rep = Await.result(s(req))
        rep.contentType === Some("text/event-stream")
      }
    }
  }

  it should "select last encoder when Accept header value doesn't match any existing encoder" in {
    check { (req: Request, accept: Accept) =>
      req.accept = s"${accept.primary}/foo"
      testService(Bootstrap.serve[AllContentTypes](pathAny).toService) { s =>
        val rep = Await.result(s(req))
        rep.contentType === Some("text/event-stream")
      }
    }
  }

  it should "return the exception occurred in endpoint's effect" in {
    val endpoint = pathAny.mapAsync { _ =>
      IO.raiseError[String](new IllegalStateException)
    }
    testService(Bootstrap.serve[Text.Plain](endpoint).toService) { s =>
      val rep = s(Request())
      assertThrows[IllegalStateException](Await.result(rep))
    }
  }
}
