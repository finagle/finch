package io.finch.wrk

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{ListeningServer, Service}
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import io.finch.internal._

/** How to benchmark this:
  *
  *   1. Run the server: sbt 'examples/runMain io.finch.wrk.Finch'
  *   1. Run wrk: wrk -t4 -c24 -d30s http://localhost:8081/
  *
  * Rule of thumb for picking values for params `t` and `c` (given that `n` is a number of logical cores your machine has, including HT):
  *
  *   - t = n
  *   - c = t * n * 1.5
  */
object Finch extends IOApp with Wrk {

  def serveR(service: Service[Request, Response]): Resource[IO, ListeningServer] =
    Resource.make(IO(serve(service))) { server =>
      IO.defer(server.close().toAsync[IO])
    }

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      service <- Endpoint[IO].lift(Payload("Hello, World!")).toServiceAs[Application.Json]
      server <- serveR(service)
    } yield server).useForever
}
