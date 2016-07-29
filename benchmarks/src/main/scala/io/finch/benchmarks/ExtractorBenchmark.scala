package io.finch.benchmarks

import com.twitter.finagle.http.Request
import io.finch._
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class ExtractorBenchmark extends FinchBenchmark {

  val empty: Input = Input(Request())
  val fooBarBaz: Input = Input(Request("/foo/bar/baz"))
  val tenTwenty: Input = Input(Request("/10/20"))
  val trueFalse: Input = Input(Request("/true/false"))

  @Benchmark
  def stringSome: Option[String] = string(fooBarBaz).value

  @Benchmark
  def stringNone: Option[String] = string(empty).value

  @Benchmark
  def intSome: Option[Int] = int(tenTwenty).value

  @Benchmark
  def intNone: Option[Int] = int(fooBarBaz).value

  @Benchmark
  def booleanSome: Option[Boolean] = boolean(trueFalse).value

  @Benchmark
  def booleanNone: Option[Boolean] = boolean(tenTwenty).value
}
