package io.finch

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.twitter.finagle.http.{Method, Request, Response, Status}
import io.finch.data.Foo
import io.finch.internal.currentTime
import shapeless.HNil

import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}

class BootstrapSpec extends FinchSpec {

  behavior of "Bootstrap"

  private val bootstrap = Bootstrap[IO]

  it should "handle both Error and Errors" in {
    check { e: Either[Error, Errors] =>
      val exception = e.fold[Exception](identity, identity)
      val ee = Endpoint[IO].liftAsync[Unit](IO.raiseError(exception))
      inside(bootstrap.serve[Text.Plain](ee).compile.apply(Request()).unsafeRunSync()) { case (_, Right(rep)) =>
        rep.status === Status.BadRequest
      }
    }
  }

  it should "catch custom exceptions in attempt" in {
    val exception = new IllegalStateException
    val endpoint = Endpoint[IO].liftAsync[Unit](IO.raiseError(exception))
    inside(bootstrap.serve[Text.Plain](endpoint).compile.apply(Request()).unsafeRunSync()) { case (_, Left(e)) =>
      e shouldBe exception
    }
  }

  it should "respond 404 if endpoint is not matched" in {
    check { req: Request =>
      val s = bootstrap.serve[Text.Plain](Endpoint[IO].empty[Unit]).compile
      inside(s(req).unsafeRunSync()) { case (_, Right(rep)) =>
        rep.status === Status.NotFound
      }
    }
  }

  it should "respond 405 if method not allowed" in {
    val a = get("foo")(Ok("get foo"))
    val b = put("foo")(Ok("put foo"))
    val c = post("foo")(Ok("post foo"))

    val s = bootstrap.configure(enableMethodNotAllowed = true).serve[Text.Plain](a :+: b).serve[Text.Plain](c).compile

    val aa = Request(Method.Get, "/foo")
    val bb = Request(Method.Put, "/foo")
    val cc = Request(Method.Post, "/foo")
    val dd = Request(Method.Delete, "/foo")

    def response(req: Request): Response =
      s(req).unsafeRunSync()._2.toTry.get

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
    val s = bootstrap.configure(enableUnsupportedMediaType = true).serve[Text.Plain](b).compile
    val i = Input.post("/").withBody[Application.Csv](Foo("bar"))
    inside(s(i.request).unsafeRunSync()) { case (_, Right(res)) =>
      res.status shouldBe Status.UnsupportedMediaType
    }
  }

  it should "match the request version" in {
    check { req: Request =>
      val s = bootstrap.serve[Text.Plain](Endpoint[IO].const(())).compile
      inside(s(req).unsafeRunSync()) { case (_, Right(rep)) =>
        rep.version === req.version
      }
    }
  }

  it should "include Date header" in {
    val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
    def parseDate(s: String): Long = ZonedDateTime.parse(s, formatter).toEpochSecond

    check { (req: Request, include: Boolean) =>
      val s = bootstrap.configure(includeDateHeader = include).serve[Text.Plain](Endpoint[IO].const(())).compile
      inside(s(req).unsafeRunSync()) { case (_, Right(rep)) =>
        val now = parseDate(currentTime())
        (include && (parseDate(rep.date.get) - now).abs <= 1) || (!include && rep.date.isEmpty)
      }
    }
  }

  it should "include Server header" in {
    check { (req: Request, include: Boolean) =>
      val s = bootstrap.configure(includeServerHeader = include).serve[Text.Plain](Endpoint[IO].const(())).compile
      inside(s(req).unsafeRunSync()) { case (_, Right(rep)) =>
        (include && rep.server === Some("Finch")) || (!include && rep.server.isEmpty)
      }
    }
  }

  it should "capture Trace for failures and successes" in {
    check { req: Request =>
      val p = req.path.split("/").drop(1)

      val endpoint = p.map(s => path(s)).foldLeft(Endpoint[IO].const(HNil: HNil))((p, e) => p :: e)

      val succ = endpoint.mapAsync(_ => IO.pure("foo"))
      val fail = endpoint.mapAsync(_ => IO.raiseError[String](new IllegalStateException))

      val (successCapture, _) = bootstrap.serve[Text.Plain](succ).compile.apply(req).unsafeRunSync()
      val (failureCapture, _) = bootstrap.serve[Text.Plain](fail).compile.apply(req).unsafeRunSync()

      successCapture.toList === p.toList && failureCapture.toList === p.toList
    }
  }
}
