package io.finch.benchmarks.request

import io.finch._, items._
import com.twitter.util.{Throw, Try}
import org.scalatest.{FlatSpec, Matchers}
import scala.reflect.classTag

class SuccessfulRequestReaderBenchmarkSpec extends FlatSpec with Matchers {

  behavior of "Successful Endpoint"

  val benchmark = new SuccessfulRequestReaderBenchmark

  it should "parse the input correctly (manual)" in {
    benchmark.hlistGenericReader shouldBe benchmark.goodFooResult
  }

  it should "parse the input correctly (derived)" in {
    benchmark.derivedReader shouldBe benchmark.goodFooResult
  }
}

class FailingRequestReaderBenchmarkSpec extends FlatSpec with Matchers {

  behavior of "Failed Endpoint"

  val benchmark = new FailingRequestReaderBenchmark

  def matchesAggregatedErrors(result: Try[Foo]) = result match {
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
    matchesAggregatedErrors(benchmark.hlistGenericReader)
  }

  it should "fail correctly on invalid input (derived)" in {
    matchesAggregatedErrors(benchmark.derivedReader)
  }
}
