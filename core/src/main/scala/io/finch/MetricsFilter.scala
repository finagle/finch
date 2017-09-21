package io.finch

import java.util.concurrent.ConcurrentHashMap

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.stats._
import com.twitter.util._

/**
  * Finagle filter to write metrics per endpoint
  */
case class MetricsFilter(receiver: StatsReceiver) extends SimpleFilter[Request, Response] {

  /**
    * Counters and stats should be cached
    *
    * This is because while capturing usage via Stat.add and Counter.incr is optimized for performance,
    * the construction of a Stat and Counter is not.
    */
  private val counters: ConcurrentHashMap[Seq[String], Counter] = new ConcurrentHashMap()
  private val stats: ConcurrentHashMap[Seq[String], Stat] = new ConcurrentHashMap()

  /**
    * Get counter by its path. Potentially, create a new one
    */
  private def counter(name: String*) = counters.computeIfAbsent(name, (t: Seq[String]) => receiver.counter(t: _*))

  /**
    * Get stats by its path. Potentially, create a new one
    */
  private def stat(name: String*) = stats.computeIfAbsent(name, (t: Seq[String]) => receiver.stat(t: _*))

  /**
    * Store stats for each request/response. Endpoint name is extracted from request path
    */
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val path = s"${request.method.name}:${request.path.replace("/","_")}"
    val start = Stopwatch.timeMillis().toFloat
    counter(path, "requests").incr()
    service(request).respond({
      case Return(out) =>
        val statusCode = out.status.code
        val genStatusCode = s"${statusCode / 100}XX"
        counter(path, "success").incr()
        counter(path, "status", statusCode.toString).incr()
        counter(path, "status", genStatusCode).incr()

        val now = Stopwatch.timeMillis().toFloat
        stat(path, "time", statusCode.toString).add(now - start)
        stat(path, "time", genStatusCode).add(now - start)
        stat(path, "response_size").add(out.content.length.toFloat)
      case Throw(_) =>
        counter(path, "failures").incr()
    })
  }
}