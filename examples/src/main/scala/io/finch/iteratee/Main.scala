package io.finch.iteratee

import scala.util.Random

import com.twitter.finagle.Http
import com.twitter.util.{Await, Future}
import io.catbird.util._
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import io.finch.syntax._
import io.iteratee.{Enumerator, Iteratee}

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
object Main {

  private val stream: Stream[Int] = Stream(Random.nextInt()).flatMap(_ => stream)

  val sumJson: Endpoint[Result] = post("sumJson" :: enumeratorJsonBody[Number]) { (enum: Enumerator[Future, Number]) =>
    enum.into(Iteratee.fold[Future, Number, Result](Result(0))(_ add _)).map(Ok)
  }

  val streamJson: Endpoint[Enumerator[Future, Number]] = get("streamJson") {
    Ok(Enumerator.enumStream[Future, Int](stream).map(Number.apply))
  }

  val isPrime: Endpoint[Enumerator[Future, IsPrime]] = post("streamPrime" :: enumeratorJsonBody[Number]) {
    (enum: Enumerator[Future, Number]) => Ok(enum.map(_.isPrime))
  }

  def main(args: Array[String]): Unit =
    Await.result(Http.server
      .withStreaming(enabled = true)
      .serve(":8081", (sumJson :+: streamJson :+: isPrime).toServiceAs[Application.Json])
    )

}

case class Result(result: Int) {
  def add(n: Number): Result = copy(result = result + n.i)
}

case class Number(i: Int) {

  def isPrime: IsPrime = IsPrime(!(2 +: (3 to Math.sqrt(i.toDouble).toInt by 2) exists (i % _ == 0)))

}

case class IsPrime(isPrime: Boolean)
