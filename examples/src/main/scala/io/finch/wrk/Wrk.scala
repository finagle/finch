package io.finch.wrk

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.{Http, ListeningServer, Service}

trait Wrk {

  case class Payload(message: String)

  protected def serve(s: Service[Request, Response]): ListeningServer =
    Http.server.withCompressionLevel(0).withStatsReceiver(NullStatsReceiver).withTracer(NullTracer).serve(":8081", s)
}
