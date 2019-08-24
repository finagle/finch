package io.finch.monix

import _root_.monix.eval.{Task, TaskApp}
import _root_.monix.execution.Scheduler
import _root_.monix.reactive.Observable
import cats.effect.{ExitCode, Resource}
import cats.implicits._
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.Future
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import scala.util.Random

/**
  * A Finch application featuring Monix Observable-based streaming support.
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
  *   $ sbt 'examples/runMain io.finch.monix.Main'
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
object Main extends TaskApp with EndpointModule[Task] {

  override implicit def scheduler: Scheduler = super.scheduler

  final case class Result(result: Int) {
    def add(n: Number): Result = copy(result = result + n.i)
  }

  final case class Number(i: Int) {
    def isPrime: IsPrime = IsPrime(!(2 :: (3 to Math.sqrt(i.toDouble).toInt by 2).toList exists (i % _ == 0)))
  }

  final case class IsPrime(isPrime: Boolean)

  private def stream: Stream[Int] = Stream.continually(Random.nextInt())

  val sumJson: Endpoint[Task, Result] = post("sumJson" :: jsonBodyStream[ObservableF, Number]) {
    o: Observable[Number] =>
      o.foldLeftL(Result(0))(_ add _).map(Ok)
  }

  val streamJson: Endpoint[Task, ObservableF[Task, Number]] = get("streamJson") {
    Ok(Observable.fromIterable(stream).map(Number.apply))
  }

  val isPrime: Endpoint[Task, ObservableF[Task, IsPrime]] =
    post("streamPrime" :: jsonBodyStream[ObservableF, Number]) { o: Observable[Number] =>
      Ok(o.map(_.isPrime))
    }

  def serve: Task[ListeningServer] = Task(
    Http.server
      .withStreaming(enabled = true)
      .serve(":8081", (sumJson :+: streamJson :+: isPrime).toServiceAs[Application.Json])
  )

  def run(args: List[String]): Task[ExitCode] = {
    val server = Resource.make(serve)(s =>
      Task.suspend(implicitly[ToAsync[Future, Task]].apply(s.close()))
    )

    server.use(_ => Task.never).as(ExitCode.Success)
  }
}
