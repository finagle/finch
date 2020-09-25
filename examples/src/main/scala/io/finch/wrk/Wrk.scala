package io.finch.wrk

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.{Http, Service}
import com.twitter.util.Await

abstract class Wrk extends App {

  case class Payload(message: String)

  protected def serve(s: Service[Request, Response]): Unit = Await.ready(
    Http.server.withCompressionLevel(0).withStatsReceiver(NullStatsReceiver).withTracer(NullTracer).serve(":8081", s)
  )
}
