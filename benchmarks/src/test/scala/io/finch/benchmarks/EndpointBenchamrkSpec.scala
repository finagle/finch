package io.finch.benchmarks

import com.twitter.util.{Throw, Try}
import io.finch._, items._
import org.scalatest.{FlatSpec, Matchers}
import scala.reflect.classTag

class SuccessfulEndpointBenchmarkSpec extends FlatSpec with Matchers {

  behavior of "Successful Endpoint"

  val benchmark = new SuccessfulEndpointBenchmark

  it should "parse the input correctly (manual)" in {
    benchmark.hlistGenericEndpoint shouldBe benchmark.goodFooResult
  }

  it should "parse the input correctly (derived)" in {
    benchmark.derivedEndpoint shouldBe benchmark.goodFooResult
  }
}

class FailingEndpointBenchmarkSpec extends FlatSpec with Matchers {

  behavior of "Failed Endpoint"

  val benchmark = new FailingEndpointBenchmark

  private[this] def matchesAggregatedErrors(result: Try[Foo]) = result match {
    case Throw(
      Error.RequestErrors(
        Seq(
          Error.NotParsed(ParamItem("d"), dTag, _: NumberFormatException),
          Error.NotParsed(ParamItem("l"), lTag, _: NumberFormatException)
        )
      )
    ) => dTag == classTag[Double] && lTag == classTag[Long]
    case _ => false
  }

  it should "fail correctly on invalid input (manual)" in {
    matchesAggregatedErrors(benchmark.hlistGenericEndpoint)
  }

  it should "fail correctly on invalid input (derived)" in {
    matchesAggregatedErrors(benchmark.derivedEndpoint)
  }
}
