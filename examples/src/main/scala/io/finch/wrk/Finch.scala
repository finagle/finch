package io.finch.wrk

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

/** How to benchmark this:
  *
  *   1. Run the server: sbt 'examples/runMain io.finch.wrk.Finch' 2. Run wrk: wrk -t4 -c24 -d30s http://localhost:8081/
  *   1. Run the server: sbt 'examples/runMain io.finch.wrk.Finch'
  *   1. Run wrk: wrk -t4 -c24 -d30s http://localhost:8081/
  *
  * Rule of thumb for picking values for params `t` and `c` (given that `n` is a number of logical cores your machine has, including HT):
  *
  *   - t = n
  *   - c = t * n * 1.5
  */
object Finch extends Wrk {

  implicit val ioRunTime: IORuntime = IORuntime.global

  Dispatcher[IO].use { implicit dispatcher =>
    IO(serve(Endpoint[IO].lift(Payload("Hello, World!")).toServiceAs[Application.Json]))
  }
}
