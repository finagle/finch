package io.finch.streaming

import java.util.concurrent.atomic.AtomicLong

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Http
import com.twitter.io.Buf
import com.twitter.util.{Await, Try}
import io.finch._
import shapeless.Witness

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

  val sum: AtomicLong = new AtomicLong(0)

  // we decode a long value from a Buf
  private[this] def bufToLong(buf: Buf): Long =
    Buf.Utf8.unapply(buf).flatMap(s => Try(s.toLong).toOption).getOrElse(0L)

  // This endpoint takes a streaming request (a stream of numbers), sums them up, and
  // returns a simple response with a total sum.
  //
  // For example, if input stream is `1, 2, 3` then output response would be `6`.
  val totalSum: Endpoint[String] = post("totalSum" :: asyncBody) { as: AsyncStream[Buf] =>
    as.foldLeft(0L)((acc, b) => acc + bufToLong(b)).map(s => Ok(s.toString))
  }

  // This endpoint takes a simple request with an integer number N and returns a
  // stream of sums so far up to this number.
  //
  // For example, if an input value is `3` then output stream would be
  // `1 (1), 3 (1 + 2), 5 (1 + 2 + 3)`.
  val sumTo: Endpoint[AsyncStream[Buf]] = post("sumTo" :: int) { to: Int =>
    def loop(n: Int, s: Int): AsyncStream[Int] =
      if (n > to) AsyncStream.empty[Int]
      else (n + s) +:: loop(n + 1, n + s)

    Ok(loop(1, 0).map(i => Buf.Utf8(i.toString)))
  }

  // This endpoint takes a streaming request (a stream of numbers) and responds
  // to each number (chunk) a sum that has been collected so far.
  //
  // For example, if input stream is `1, 2, 3` then output stream would be `1, 3, 6`.
  val sumSoFar: Endpoint[AsyncStream[Buf]] =
    post("sumSoFar" :: asyncBody) { as: AsyncStream[Buf] =>
      Ok(as.map(b =>
        Buf.Utf8(sum.addAndGet(bufToLong(b)).toString)
      ))
    }

  Await.result(Http.server
    .withStreaming(enabled = true)
    .serve(":8081", (sumSoFar :+: sumTo :+: totalSum).toServiceAs[Witness.`"text/plain"`.T])
  )
}
