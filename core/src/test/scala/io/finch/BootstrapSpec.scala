package io.finch

import cats.effect.IO
import com.twitter.finagle.http.{Method, Request, Status}
import com.twitter.util.Await
import io.finch.data.Foo
import io.finch.internal.currentTime
import java.time.{ZonedDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import shapeless.HNil

class BootstrapSpec extends FinchSpec {

  behavior of "Bootstrap"

  it should "handle both Error and Errors" in {
    check { e: Either[Error, Errors] =>
      val exception = e.fold[Exception](identity, identity)

      val ee = Endpoint[IO].liftAsync[Unit](IO.raiseError(exception))
      val rep = Await.result(ee.toServiceAs[Text.Plain].apply(Request()))
      rep.status === Status.BadRequest
    }
  }

  it should "respond 404 if endpoint is not matched" in {
    check { req: Request =>
      val s = Endpoint[IO].empty[Unit].toServiceAs[Text.Plain]
      val rep = Await.result(s(req))

      rep.status === Status.NotFound
    }
  }

  it should "respond 405 if method not allowed" in {
    val a = get("foo") { Ok("get foo") }
    val b = put("foo") { Ok("put foo") }
    val c = post("foo") { Ok("post foo") }

    val s = Bootstrap
      .configure(enableMethodNotAllowed = true)
      .serve[Text.Plain](a :+: b)
      .serve[Text.Plain](c)
      .toService

    val aa = Request(Method.Get, "/foo")
    val bb = Request(Method.Put, "/foo")
    val cc = Request(Method.Post, "/foo")
    val dd = Request(Method.Delete, "/foo")

    Await.result(s(Request(Method.Get, "/bar"))).status shouldBe Status.NotFound

    Await.result(s(aa)).contentString shouldBe "get foo"
    Await.result(s(bb)).contentString shouldBe "put foo"
    Await.result(s(cc)).contentString shouldBe "post foo"

    val rep = Await.result(s(dd))
    rep.status shouldBe Status.MethodNotAllowed
    rep.allow shouldBe Some("POST,GET,PUT")
  }

  it should "respond 415 if media type is not supported" in {
    val b = body[Foo, Text.Plain]
    val s = Bootstrap.configure(enableUnsupportedMediaType = true)
      .serve[Text.Plain](b)
      .toService

    val i = Input.post("/").withBody[Application.Csv](Foo("bar"))

    Await.result(s(i.request)).status shouldBe Status.UnsupportedMediaType
  }

  it should "match the request version" in {
    check { req: Request =>
      val s = Endpoint[IO].const(()).toServiceAs[Text.Plain]
      val rep = Await.result(s(req))

      rep.version === req.version
    }
  }

  it should "include Date header" in {
    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
    def parseDate(s: String): Long = ZonedDateTime.parse(s, formatter).toEpochSecond

    check { (req: Request, include: Boolean) =>
      val s = Bootstrap.configure(includeDateHeader = include)
        .serve[Text.Plain](Endpoint[IO].const(()))
        .toService

      val rep = Await.result(s(req))
      val now = parseDate(currentTime())

      (include && (parseDate(rep.date.get) - now).abs <= 1) || (!include && rep.date.isEmpty)
    }
  }

  it should "include Server header" in {
    check { (req: Request, include: Boolean) =>
      val s = Bootstrap.configure(includeServerHeader = include)
        .serve[Text.Plain](Endpoint[IO].const(()))
        .toService

      val rep = Await.result(s(req))

      (include && rep.server === Some("Finch")) || (!include && rep.server.isEmpty)
    }
  }

  it should "capture trace when needed" in {
    check { req: Request =>
      val p = req.path.split("/").drop(1)
      val e = p
        .map(s => path(s))
        .foldLeft(Endpoint[IO].const(HNil : HNil))((p, e) => p :: e)
        .map(_ => "foo")
      val s = e.toServiceAs[Text.Plain]

      val captured = Await.result(Trace.capture(s(req).map(_ => Trace.captured)))
      captured.toList === p.toList
    }
  }
}
