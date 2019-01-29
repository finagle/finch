package io.finch.middleware

import cats.effect.IO
import com.twitter.finagle.Http
import com.twitter.finagle.http.{Response, Status}
import com.twitter.util.{Await, Time}
import io.finch._

/**
  * Small Finch hello world application serving endpoint protected by serious authentication
  * where each request & response are also logged and measured.
  *
  * This is achieved using Kleisli-based middleware together with [[Bootstrap.compile]]
  *
  * Use the following curl commands to test it:
  *
  * {{{
  *   curl -v -H "Authorization: secret" http://localhost:8081/hello
  *   curl -V -H "Authorization: wrong" http://localhost:8081/hello
  * }}}
  */
object Main extends App with Endpoint.Module[IO] {

  val helloWorld: Endpoint[IO, String] = get("hello") {
    Ok("Hello world")
  }

  def auth(compiled: Endpoint.Compiled[IO]): Endpoint.Compiled[IO] = {
    Endpoint.Compiled[IO] {
      case req if req.authorization.contains("secret") => compiled(req)
      case _ => IO.pure(Trace.empty -> Right(Response(Status.Unauthorized)))
    }
  }

  def logging(compiled: Endpoint.Compiled[IO]): Endpoint.Compiled[IO] = {
    compiled.tapWith((req, res) => {
      print(s"Request: $req\n")
      print(s"Response: $res\n")
      res
    })
  }

  def stats(compiled: Endpoint.Compiled[IO]): Endpoint.Compiled[IO] = {
    Endpoint.Compiled[IO] { req =>
      val start = Time.now
      compiled.map {
        case r @ (trace, _) =>
          print(s"Response time: ${Time.now.diff(start)}. Trace: $trace\n")
          r
      }.run(req)
    }
  }

  val compiled = stats(logging(auth(Bootstrap.serve[Text.Plain](helloWorld).compile)))

  Await.ready(Http.server.serve(":8081", Endpoint.toService(compiled)))

}
