package io.finch.wrk

import com.twitter.finagle.Http
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer

trait Wrk {

  case class Payload(message: String)

  protected def server: Http.Server =
    Http.server.withCompressionLevel(0).withStatsReceiver(NullStatsReceiver).withTracer(NullTracer)
}
