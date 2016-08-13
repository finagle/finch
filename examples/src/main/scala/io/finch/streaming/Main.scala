package io.finch.streaming

import java.util.concurrent.atomic.AtomicLong

import cats.Show
import cats.std.long._
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
*
* Use the following sbt command to run the application.
*
* {{{
*   $ sbt 'examples/runMain io.finch.streaming.Main'
* }}}
*/
object Main extends App {

  // An examples domain type.
  case class Example(x: Int)
  object Example {
    implicit val show: Show[Example] = Show.fromToString
  }

  val sum: AtomicLong = new AtomicLong(0)

  // we decode a long value from a Buf
  private[this] def bufToLong(buf: Buf): Long =
    Buf.Utf8.unapply(buf).flatMap(s => Try(s.toLong).toOption).getOrElse(0L)

  // This endpoint takes a streaming request (a stream of numbers), sums them up, and
  // returns a simple response with a total sum.
  //
  // For example, if input stream is `1, 2, 3` then output response would be `6`.
  val totalSum: Endpoint[Long] = post("totalSum" :: asyncBody) { as: AsyncStream[Buf] =>
    as.foldLeft(0L)((acc, b) => acc + bufToLong(b)).map(Ok)
  }

  // This endpoint takes a simple request with an integer number N and returns a
  // stream of sums so far up to this number.
  //
  // For example, if an input value is `3` then output stream would be
  // `1 (1), 3 (1 + 2), 5 (1 + 2 + 3)`.
  val sumTo: Endpoint[AsyncStream[Long]] = post("sumTo" :: long) { to: Long =>
    def loop(n: Long, s: Long): AsyncStream[Long] =
      if (n > to) AsyncStream.empty[Long]
      else (n + s) +:: loop(n + 1, n + s)

    Ok(loop(1, 0))
  }

  // This endpoint will stream back a given number of `Example` objects in plain/text.
  val examples: Endpoint[AsyncStream[Example]] = get("examples" :: int) { num: Int =>
    Ok(AsyncStream.fromSeq(List.tabulate(num)(i => Example(i))))
  }

  // This endpoint takes a streaming request (a stream of numbers) and responds
  // to each number (chunk) a sum that has been collected so far.
  //
  // For example, if input stream is `1, 2, 3` then output stream would be `1, 3, 6`.
  val sumSoFar: Endpoint[AsyncStream[Long]] =
    post("sumSoFar" :: asyncBody) { as: AsyncStream[Buf] =>
      Ok(as.map(b => sum.addAndGet(bufToLong(b))))
    }

  Await.result(Http.server
    .withStreaming(enabled = true)
    .serve(":8081", (sumSoFar :+: sumTo :+: totalSum :+: examples).toServiceAs[Text.Plain])
  )
}
