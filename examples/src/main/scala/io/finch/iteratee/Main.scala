package io.finch.iteratee

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.Future
import io.circe.generic.auto._
import io.finch._
import io.finch.catsEffect._
import io.finch.circe._
import io.iteratee.{Enumerator, Iteratee}
import scala.util.Random

/**
  * A Finch application featuring iteratee-based streaming support.
  * This approach is more advanced and performant then basic [[com.twitter.concurrent.AsyncStream]]
  *
  * There are three endpoints in this example:
  *
  *  1. `sumJson` - streaming request
  *  2. `streamJson` - streaming response
  *  3. `isPrime` - end-to-end (request - response) streaming
  *
  * Use the following sbt command to run the application.
  *
  * {{{
  *   $ sbt 'examples/runMain io.finch.iteratee.Main'
  * }}}
  *
  * Use the following HTTPie/curl commands to test endpoints.
  *
  * {{{
  *   $ curl -X POST --header "Transfer-Encoding: chunked" -d '{"i": 40} {"i": 2}' localhost:8081/sumJson
  *
  *   $ http --stream GET :8081/streamJson
  *
  *   $ curl -X POST --header "Transfer-Encoding: chunked" -d '{"i": 40} {"i": 42}' localhost:8081/streamPrime
  * }}}
  */
object Main extends IOApp {

  final case class Result(result: Int) {
    def add(n: Number): Result = copy(result = result + n.i)
  }

  final case class Number(i: Int) {
    def isPrime: IsPrime = IsPrime(!(2 :: (3 to Math.sqrt(i.toDouble).toInt by 2).toList exists (i % _ == 0)))
  }

  final case class IsPrime(isPrime: Boolean)

  private def stream: Stream[Int] = Stream.continually(Random.nextInt())

  val sumJson: Endpoint[IO, Result] = post("sumJson" :: jsonBodyStream[Enumerator, Number]) {
    enum: Enumerator[IO, Number] =>
      enum.into(Iteratee.fold[IO, Number, Result](Result(0))(_ add _)).map(Ok)
  }

  val streamJson: Endpoint[IO, Enumerator[IO, Number]] = get("streamJson") {
    Ok(Enumerator.enumStream[IO, Int](stream).map(Number.apply))
  }

  val isPrime: Endpoint[IO, Enumerator[IO, IsPrime]] =
    post("streamPrime" :: jsonBodyStream[Enumerator, Number]) { enum: Enumerator[IO, Number] =>
      Ok(enum.map(_.isPrime))
    }

  def serve: IO[ListeningServer] = IO(
    Http.server
      .withStreaming(enabled = true)
      .serve(":8081", (sumJson :+: streamJson :+: isPrime).toServiceAs[Application.Json])
  )

  def run(args: List[String]): IO[ExitCode] = {
    val server = Resource.make(serve)(s =>
      IO.suspend(implicitly[ToAsync[Future, IO]].apply(s.close()))
    )

    server.use(_ => IO.never).as(ExitCode.Success)
  }
}
