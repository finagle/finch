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
  *
  * !Disclaimer: most likely you would need to use proper libraries for logging, auth, and metrics
  * instead but their use will be quite similar.
  */
object Main extends App with Endpoint.Module[IO] {

  val helloWorld: Endpoint[IO, String] = get("hello") {
    Ok("Hello world")
  }

  val auth: Endpoint.Compiled[IO] => Endpoint.Compiled[IO] = compiled => {
    Endpoint.Compiled[IO] {
      case req if req.authorization.contains("secret") => compiled(req)
      case _                                           => IO.pure(Trace.empty -> Right(Response(Status.Unauthorized)))
    }
  }

  val logging: Endpoint.Compiled[IO] => Endpoint.Compiled[IO] = compiled => {
    compiled.tapWithF { (req, res) =>
      IO(print(s"Request: $req\n")) *> IO(print(s"Response: $res\n")) *> IO.pure(res)
    }
  }

  val stats: Endpoint.Compiled[IO] => Endpoint.Compiled[IO] = compiled => {
    val now = IO(Time.now)
    Endpoint.Compiled[IO] { req =>
      for {
        start <- now
        traceAndResponse <- compiled(req)
        (trace, response) = traceAndResponse
        stop <- now
        _ <- IO(print(s"Response time: ${stop.diff(start)}. Trace: $trace\n"))
      } yield (trace, response)
    }
  }

  val filters = Function.chain(Seq(stats, logging, auth))
  val compiled = filters(Bootstrap.serve[Text.Plain](helloWorld).compile)

  Await.ready(Http.server.serve(":8081", Endpoint.toService(compiled)))

}
