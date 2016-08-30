package io.finch.benchmarks

import io.finch._
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class ExtractorBenchmark extends FinchBenchmark {

  val empty: Input = Input.get("/")
  val fooBarBaz: Input = Input.get("/foo/bar/baz")
  val tenTwenty: Input = Input.get("/10/20")
  val trueFalse: Input = Input.get("/true/false")

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
