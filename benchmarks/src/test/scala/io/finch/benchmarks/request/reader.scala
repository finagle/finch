package io.finch.benchmarks.request

import com.twitter.util.{Throw, Try}
import org.scalatest.{FlatSpec, Matchers}
import io.finch.request.{NotParsed, RequestErrors}
import io.finch.request.items.ParamItem
import scala.reflect.classTag

class SuccessfulRequestReaderBenchmarkSpec extends FlatSpec with Matchers {
  val benchmark = new SuccessfulRequestReaderBenchmark

  "The monadic reader" should "parse the input correctly" in {
    benchmark.monadicReader shouldBe benchmark.goodFooResult
  }

  "The applicative ~ reader" should "parse the input correctly" in {
    benchmark.tildeReader shouldBe benchmark.goodFooResult
  }

  "The generic applicative HList-based reader" should "parse the input correctly" in {
    benchmark.hlistGenericReader shouldBe benchmark.goodFooResult
  }

  "The applicative HList-based reader with ~>" should "parse the input correctly" in {
    benchmark.hlistApplyReader shouldBe benchmark.goodFooResult
  }

  "The derived reader" should "parse the input correctly" in {
    benchmark.derivedReader shouldBe benchmark.goodFooResult
  }
}

class FailingRequestReaderBenchmarkSpec extends FlatSpec with Matchers {
  val benchmark = new FailingRequestReaderBenchmark

  def matchesAggregatedErrors(result: Try[Foo]) = result match {
    case Throw(
      RequestErrors(
        Seq(
          NotParsed(ParamItem("d"), dTag, _: NumberFormatException),
          NotParsed(ParamItem("l"), lTag, _: NumberFormatException)
        )
      )
    ) => dTag == classTag[Double] && lTag == classTag[Long]
    case _ => false
  }

  "The monadic reader" should "fail with a single error on invalid input" in {
    benchmark.monadicReader should matchPattern {
      case Throw(
        NotParsed(ParamItem("d"), tag, _: NumberFormatException)
      ) if tag == classTag[Double] =>
    }
  }

  "The applicative ~ reader" should "fail correctly on invalid input" in {
    matchesAggregatedErrors(benchmark.tildeReader)
  }

  "The generic applicative HList-based reader" should "fail correctly on invalid input" in {
    matchesAggregatedErrors(benchmark.hlistGenericReader)
  }

  "The applicative HList-based reader with ~>" should "fail correctly on invalid input" in {
    matchesAggregatedErrors(benchmark.hlistApplyReader)
  }

  "The derived reader" should "fail correctly on invalid input" in {
    matchesAggregatedErrors(benchmark.derivedReader)
  }
}
