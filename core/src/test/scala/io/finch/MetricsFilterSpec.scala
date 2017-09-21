package io.finch

import scala.util.Try

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.util.{Await, Future}
import org.scalatest._

class MetricsFilterSpec extends FinchSpec with OneInstancePerTest {

  private val inMemoryStats = new InMemoryStatsReceiver
  private val metricsFilter = new MetricsFilter(inMemoryStats)

  private def successfulService: Service[Request, Response] = {
    metricsFilter.andThen((_: Request) => Future(Response(Status.Ok)))
  }

  private def hello[A](checkStats: InMemoryStatsReceiver  => A) = {
    Await.result(successfulService(Request("/hello")))
    checkStats(inMemoryStats)
  }

  private def endpoint(str: String*): Seq[String] = {
    Seq("GET:_hello") ++ str
  }

  it should "count nothing without requests" in {
    inMemoryStats.counters shouldBe 'empty
  }


  it should "count number of requests" in {
    hello {
      _.counters(endpoint("requests")) shouldBe 1
    }
  }

  it should "count number of successful responses" in {
    hello {
      _.counters(endpoint("success")) shouldBe 1
    }
  }

  it should "count status codes of successful responses" in {
    hello  { stat =>
      stat.counters(endpoint("status", "200")) shouldBe 1
      stat.counters(endpoint("status", "2XX")) shouldBe 1
    }
  }

  it should "count time spent for processing" in {
    hello { stat =>
      stat.stats(endpoint("time", "200")).nonEmpty shouldBe true
      stat.stats(endpoint("time", "2XX")).nonEmpty shouldBe true
    }
  }

  it should "count failures if service has failed with exception" in {
    val service = metricsFilter.andThen((_: Request) =>
      Future.exception(new IllegalStateException)
    )
    Try(Await.result(service(Request("/hello"))))
    inMemoryStats.counters(endpoint("failures")) shouldBe 1
  }

}
