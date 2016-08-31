package io.finch.benchmarks

import com.twitter.util.Try
import io.finch._
import org.openjdk.jmh.annotations._

case class Foo(s: String, d: Double, i: Int, l: Long, b: Boolean)

/**
 * Compares the performance of different ways of creating endpoints on valid input.
 *
 * The following command will run the endpoint benchmarks with reasonable settings:
 *
 * > sbt 'project benchmarks' 'run -prof gc io.finch.benchmarks.EndpointBenchmark'
 */
@State(Scope.Benchmark)
class SuccessfulEndpointBenchmark extends FinchBenchmark with FooEndpointsAndRequests {
  @Benchmark
  def hlistGenericEndpoint: Foo = hlistGenericFooReader(goodFooRequest).value.get

  @Benchmark
  def derivedEndpoint: Foo = derivedFooReader(goodFooRequest).value.get
}

/**
 * Compares the performance of different ways of creating endpoints on invalid input.
 *
 * Note that the monadic reader shouldn't be compared directly to the other
 * readers for invalid inputs, since it fails on the first error.
 */
@State(Scope.Benchmark)
class FailingEndpointBenchmark extends FinchBenchmark with FooEndpointsAndRequests {
  @Benchmark
  def hlistGenericEndpoint: Try[Foo] = hlistGenericFooReader(badFooRequest).tryValue.get

  @Benchmark
  def derivedEndpoint: Try[Foo] = derivedFooReader(badFooRequest).tryValue.get
}

/**
 * Provides endpoints and example requests.
 */
trait FooEndpointsAndRequests {
  val hlistGenericFooReader: Endpoint[Foo] = (
    param("s") ::
    param("d").as[Double] ::
    param("i").as[Int] ::
    param("l").as[Long] ::
    param("b").as[Boolean]
  ).as[Foo]

  val derivedFooReader: Endpoint[Foo] = Endpoint.derive[Foo].fromParams

  val goodFooRequest: Input = Input.get("/",
    "s" -> "Man hands on misery to man. It deepens like a coastal shelf.",
    "d" -> "0.234567",
    "i" -> "123456",
    "l" -> "1234567890",
    "b" -> "true"
  )

  val badFooRequest: Input = Input.get("/",
    "s" -> "Man hands on misery to man. It deepens like a coastal shelf.",
    "d" -> "0.23h4567",
    "i" -> "123456",
    "l" -> "1234567890x",
    "b" -> "true"
  )

  val goodFooResult: Foo = Foo(
    "Man hands on misery to man. It deepens like a coastal shelf.",
    0.234567,
    123456,
    1234567890L,
    true
  )
}
