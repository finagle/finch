package io.finch.benchmarks.request

import java.util.concurrent.TimeUnit

import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Try}
import io.finch._
import org.openjdk.jmh.annotations._

case class Foo(s: String, d: Double, i: Int, l: Long, b: Boolean)

/**
 * Compares the performance of different ways of creating request readers on
 * valid input.
 *
 * The following command will run the request reader benchmarks with reasonable
 * settings:
 *
 * > sbt 'project benchmarks' 'run -prof gc io.finch.benchmarks.request.*Benchmark.*'
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class SuccessfulRequestReaderBenchmark extends FooReadersAndRequests {
  @Benchmark
  def hlistGenericReader: Foo = hlistGenericFooReader(goodFooRequest).value.get

  @Benchmark
  def derivedReader: Foo = derivedFooReader(goodFooRequest).value.get
}

/**
 * Compares the performance of different ways of creating request readers on
 * invalid input.
 *
 * Note that the monadic reader shouldn't be compared directly to the other
 * readers for invalid inputs, since it fails on the first error.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FailingRequestReaderBenchmark extends FooReadersAndRequests {
  @Benchmark
  def hlistGenericReader: Try[Foo] = hlistGenericFooReader(badFooRequest).poll.get

  @Benchmark
  def derivedReader: Try[Foo] = derivedFooReader(badFooRequest).poll.get
}

/**
 * Provides request readers and example requests.
 */
class FooReadersAndRequests {
  val hlistGenericFooReader: Endpoint[Foo] = (
    param("s") ::
    param("d").as[Double] ::
    param("i").as[Int] ::
    param("l").as[Long] ::
    param("b").as[Boolean]
  ).as[Foo]

  val derivedFooReader: Endpoint[Foo] = Endpoint.derive[Foo].fromParams

  val goodFooRequest: Input = Input(Request(
    "s" -> "Man hands on misery to man. It deepens like a coastal shelf.",
    "d" -> "0.234567",
    "i" -> "123456",
    "l" -> "1234567890",
    "b" -> "true"
  ))

  val badFooRequest: Input = Input(Request(
    "s" -> "Man hands on misery to man. It deepens like a coastal shelf.",
    "d" -> "0.23h4567",
    "i" -> "123456",
    "l" -> "1234567890x",
    "b" -> "true"
  ))

  val goodFooResult: Foo = Foo(
    "Man hands on misery to man. It deepens like a coastal shelf.",
    0.234567,
    123456,
    1234567890L,
    true
  )
}
