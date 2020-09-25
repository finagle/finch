package io.finch

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

import cats.effect.IO
import com.twitter.finagle.http.{Method, Request, Response, Status}
import io.finch.data.Foo
import io.finch.internal.currentTime
import shapeless.HNil

class BootstrapSpec extends FinchSpec {

  behavior of "Bootstrap"

  it should "handle both Error and Errors" in {
    check { e: Either[Error, Errors] =>
      val exception = e.fold[Exception](identity, identity)

      val ee = Endpoint[IO].liftAsync[Unit](IO.raiseError(exception))
      val (_, Right(rep)) = ee.compileAs[Text.Plain].apply(Request()).unsafeRunSync()
      rep.status === Status.BadRequest
    }
  }

  it should "catch custom exceptions in attempt" in {
    val exception = new IllegalStateException
    val endpoint = Endpoint[IO].liftAsync[Unit](IO.raiseError(exception))
    val (_, Left(e)) = endpoint.compileAs[Text.Plain].apply(Request()).unsafeRunSync()
    e shouldBe exception
  }

  it should "respond 404 if endpoint is not matched" in {
    check { req: Request =>
      val s = Endpoint[IO].empty[Unit].compileAs[Text.Plain]
      val (_, Right(rep)) = s(req).unsafeRunSync()

      rep.status === Status.NotFound
    }
  }

  it should "respond 405 if method not allowed" in {
    val a = get("foo")(Ok("get foo"))
    val b = put("foo")(Ok("put foo"))
    val c = post("foo")(Ok("post foo"))

    val s = Bootstrap.configure(enableMethodNotAllowed = true).serve[Text.Plain](a :+: b).serve[Text.Plain](c).compile

    val aa = Request(Method.Get, "/foo")
    val bb = Request(Method.Put, "/foo")
    val cc = Request(Method.Post, "/foo")
    val dd = Request(Method.Delete, "/foo")

    def response(req: Request): Response = {
      val (_, Right(res)) = s(req).unsafeRunSync()
      res
    }
    response(Request(Method.Get, "/bar")).status shouldBe Status.NotFound

    response(aa).contentString shouldBe "get foo"
    response(bb).contentString shouldBe "put foo"
    response(cc).contentString shouldBe "post foo"

    val rep = response(dd)
    rep.status shouldBe Status.MethodNotAllowed
    rep.allow shouldBe Some("POST,GET,PUT")
  }

  it should "respond 415 if media type is not supported" in {
    val b = body[Foo, Text.Plain]
    val s = Bootstrap.configure(enableUnsupportedMediaType = true).serve[Text.Plain](b).compile

    val i = Input.post("/").withBody[Application.Csv](Foo("bar"))

    val (_, Right(res)) = s(i.request).unsafeRunSync()
    res.status shouldBe Status.UnsupportedMediaType
  }

  it should "match the request version" in {
    check { req: Request =>
      val s = Endpoint[IO].const(()).compileAs[Text.Plain]
      val (_, Right(rep)) = s(req).unsafeRunSync()

      rep.version === req.version
    }
  }

  it should "include Date header" in {
    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
    def parseDate(s: String): Long = ZonedDateTime.parse(s, formatter).toEpochSecond

    check { (req: Request, include: Boolean) =>
      val s = Bootstrap.configure(includeDateHeader = include).serve[Text.Plain](Endpoint[IO].const(())).compile

      val (_, Right(rep)) = s(req).unsafeRunSync()
      val now = parseDate(currentTime())

      (include && (parseDate(rep.date.get) - now).abs <= 1) || (!include && rep.date.isEmpty)
    }
  }

  it should "include Server header" in {
    check { (req: Request, include: Boolean) =>
      val s = Bootstrap.configure(includeServerHeader = include).serve[Text.Plain](Endpoint[IO].const(())).compile

      val (_, Right(rep)) = s(req).unsafeRunSync()

      (include && rep.server === Some("Finch")) || (!include && rep.server.isEmpty)
    }
  }

  it should "capture Trace for failures and successes" in {
    check { req: Request =>
      val p = req.path.split("/").drop(1)

      val endpoint = p.map(s => path(s)).foldLeft(Endpoint[IO].const(HNil: HNil))((p, e) => p :: e)

      val succ = endpoint.mapAsync(_ => IO.pure("foo"))
      val fail = endpoint.mapAsync(_ => IO.raiseError[String](new IllegalStateException))

      val (successCapture, _) = succ.compileAs[Text.Plain].apply(req).unsafeRunSync()
      val (failureCapture, _) = fail.compileAs[Text.Plain].apply(req).unsafeRunSync()

      successCapture.toList === p.toList && failureCapture.toList === p.toList
    }
  }
}
