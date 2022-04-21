package io.finch.wrk

import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.Http

trait Wrk {

  case class Payload(message: String)

  protected def server: Http.Server =
    Http.server.withCompressionLevel(0).withStatsReceiver(NullStatsReceiver).withTracer(NullTracer)
}
