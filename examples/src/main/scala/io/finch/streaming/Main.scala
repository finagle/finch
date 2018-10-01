package io.finch.streaming

import java.util.concurrent.atomic.AtomicLong

import cats.Show
import cats.effect.IO
import cats.instances.long._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Http
import com.twitter.io.Buf
import com.twitter.util.{Await, Try}
import io.finch._

/**
* A simple Finch application featuring very basic, `Buf`-based streaming support.
*
* There are three endpoints in this example:
*
*  1. `totalSum` - streaming request
*  2. `sumTo` - streaming response
*  3. `sumSoFar` - end-to-end (request - response) streaming
*  4. `examples` - streaming response of examples objects
*
* Use the following sbt command to run the application.
*
* {{{
*   $ sbt 'examples/runMain io.finch.streaming.Main'
* }}}
*
* Use the following HTTPie/curl commands to test endpoints.
*
* {{{
*   $ curl -X POST --header "Transfer-Encoding: chunked" -d 4 localhost:8081/totalSum
*
*   $ http --stream POST :8081/sumTo/3
*
*   $ curl -X POST --header "Transfer-Encoding: chunked" -d 4 localhost:8081/sumSoFar
*   $ curl -X POST --header "Transfer-Encoding: chunked" -d 3 localhost:8081/sumSoFar
*
*   $ http --stream GET :8081/examples/3
* }}}
*/
object Main extends Endpoint.Module[IO] {

  // we decode a long value from a Buf
  private[this] def bufToLong(buf: Buf): Long =
    Buf.Utf8.unapply(buf).flatMap(s => Try(s.toLong).toOption).getOrElse(0L)

  // This endpoint takes a streaming request (a stream of numbers), sums them up, and
  // returns a simple response with a total sum.
  //
  // For example, if input stream is `1, 2, 3` then output response would be `6`.
  def totalSum: Endpoint[IO, Long] = post("totalSum" :: asyncBody) { as: AsyncStream[Buf] =>
    IO.async[Long](cb =>
      as
        .foldLeft(0L)((acc, b) => acc + bufToLong(b))
        .onSuccess(i => cb(Right(i)))
        .onFailure(t => cb(Left(t)))
    ).map(Ok)
  }

  // This endpoint takes a simple request with an integer number N and returns a
  // stream of sums so far up to this number.
  //
  // For example, if an input value is `3` then output stream would be
  // `1 (1), 3 (1 + 2), 5 (1 + 2 + 3)`.
  def sumTo: Endpoint[IO, AsyncStream[Long]] = post("sumTo" :: path[Long]) { to: Long =>
    def loop(n: Long, s: Long): AsyncStream[Long] =
      if (n > to) AsyncStream.empty[Long]
      else (n + s) +:: loop(n + 1, n + s)

    Ok(loop(1, 0))
  }

  val sum: AtomicLong = new AtomicLong(0)
  // This endpoint takes a streaming request (a stream of numbers) and responds
  // to each number (chunk) a sum that has been collected so far.
  //
  // For example, if input stream is `1, 2, 3` then output stream would be `1, 3, 6`.
  def sumSoFar: Endpoint[IO, AsyncStream[Long]] =
    post("sumSoFar" :: asyncBody) { as: AsyncStream[Buf] =>
      Ok(as.map(b => sum.addAndGet(bufToLong(b))))
    }

  // An example domain type.
  case class Example(x: Int)
  object Example {
    implicit val show: Show[Example] = Show.fromToString
  }
  // This endpoint will stream back a given number of `Example` objects in plain/text.
  def examples: Endpoint[IO, AsyncStream[Example]] = get("examples" :: path[Int]) { num: Int =>
    Ok(AsyncStream.fromSeq(List.tabulate(num)(i => Example(i))))
  }

  def main(args: Array[String]): Unit = Await.result(
    Http.server
      .withStreaming(enabled = true)
      .serve(":8081", (sumSoFar :+: sumTo :+: totalSum :+: examples).toServiceAs[Text.Plain])
  )
}
