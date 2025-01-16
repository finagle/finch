package io.finch

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.Await
import io.finch.data.Foo
import org.scalatest.Assertion
import shapeless._

class EndToEndSpec extends FinchSpec[IO] {
  behavior of "Finch"

  type AllContentTypes = Application.Json :+: Application.AtomXml :+: Application.Csv :+:
    Application.Javascript :+: Application.OctetStream :+: Application.RssXml :+:
    Application.WwwFormUrlencoded :+: Application.Xml :+: Text.Plain :+: Text.Html :+: Text.EventStream :+: CNil

  implicit private def encodeHNil[CT <: String]: Encode.Aux[HNil, CT] =
    Encode.instance((_, _) => Buf.Utf8("hnil"))

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

  private def testService[CTS]: EndToEndSpec.ServiceTest[CTS] =
    new EndToEndSpec.ServiceTest(IORuntime.global)

  it should "convert coproduct Endpoints into Services" in {
    implicit val encodeException: Encode.Text[Exception] =
      Encode.text((_, cs) => Buf.ByteArray.Owned("ERR!".getBytes(cs.name)))

    testService[Text.Plain](
      get("foo" :: path[String]) { s: String => Ok(Foo(s)) } :+:
        get("bar")(Created("bar")) :+:
        get("baz")(BadRequest(new IllegalArgumentException("foo")): Output[Unit]) :+:
        get("qux" :: param[Foo]("foo")) { f: Foo => Created(f) }
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
    testService[Text.Plain](get("foo")(Created("bar"))) { s =>
      val rep = Await.result(s(Request("/foo")))
      rep.contentString shouldBe "bar"
      rep.status shouldBe Status.Created
    }
  }

  it should "ignore Accept header when single type is used for serve" in {
    testService[Text.Plain](pathAny) { service =>
      check { req: Request =>
        val rep = Await.result(service(req))
        rep.contentType === Some("text/plain")
      }
    }
  }

  it should "respect Accept header when coproduct type is used for serve" in {
    testService[AllContentTypes](pathAny) { s =>
      check { req: Request =>
        val rep = Await.result(s(req))
        rep.contentType === req.accept.headOption
      }
    }
  }

  it should "ignore order of values in Accept header and use first appropriate encoder in coproduct" in {
    testService[AllContentTypes](pathAny) { s =>
      check { (req: Request, accept: Accept) =>
        val a = s"${accept.primary}/${accept.sub}"
        req.accept = a +: req.accept
        val rep = Await.result(s(req))
        val first = allContentTypes.collectFirst {
          case ct if req.accept.contains(ct) => ct
        }
        rep.contentType === first
      }
    }
  }

  it should "select last encoder when Accept header is missing/empty" in {
    testService[AllContentTypes](pathAny) { s =>
      check { req: Request =>
        req.headerMap.remove(Fields.Accept)
        val rep = Await.result(s(req))
        rep.contentType === Some("text/event-stream")
      }
    }
  }

  it should "select last encoder when Accept header value doesn't match any existing encoder" in {
    testService[AllContentTypes](pathAny) { s =>
      check { (req: Request, accept: Accept) =>
        req.accept = s"${accept.primary}/foo"
        val rep = Await.result(s(req))
        rep.contentType === Some("text/event-stream")
      }
    }
  }

  it should "return the exception occurred in endpoint's effect" in {
    val endpoint = pathAny.mapAsync { _ =>
      IO.raiseError[String](new IllegalStateException)
    }
    testService[Text.Plain](endpoint) { s =>
      val rep = s(Request())
      assertThrows[IllegalStateException](Await.result(rep))
    }
  }

  it should "fail with 406 Not Acceptable on Content-Type negotiation failure when enabled" in {
    testService[AllContentTypes](pathAny, _.configure(enableNotAcceptable = true)) { s =>
      check { (req: Request, accept: List[Accept]) =>
        req.accept = accept.map(_.primary + "/foo")
        val rep = Await.result(s(req))
        rep.status === Status.NotAcceptable
      }
    }
  }

  it should "fail with 406 Not Acceptable on Content-Type mismatch when enabled" in {
    testService[Text.Plain](pathAny, _.configure(enableNotAcceptable = true)) { s =>
      check { (req: Request, accept: Accept) =>
        req.accept = accept.primary + "/foo"
        val rep = Await.result(s(req))
        rep.status === Status.NotAcceptable
      }
    }
  }

  it should "succeed when there is no Accept header even though Not Acceptable is enabled" in
    testService[Text.Plain](pathAny, _.configure(enableNotAcceptable = true)) { s =>
      check { req: Request =>
        req.accept = Nil
        val rep = Await.result(s(req))
        rep.status === Status.Ok
      }
    }
}

object EndToEndSpec {
  private class ServiceTest[CTS](private val runtime: IORuntime) extends AnyVal {
    def apply[E](endpoint: Endpoint[IO, E], cfg: Bootstrap[IO, HNil, HNil] => Bootstrap[IO, HNil, HNil] = identity)(
        assertions: Service[Request, Response] => Assertion
    )(implicit ts: Compile[IO, Endpoint[IO, E] :: HNil, CTS :: HNil]): Assertion =
      cfg(Bootstrap[IO]).serve[CTS](endpoint).toService.use(s => IO(assertions(s))).unsafeRunSync()(runtime)
  }
}
