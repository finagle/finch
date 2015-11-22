package io.finch.benchmarks

import com.twitter.finagle.http.Request
import io.finch._
import org.openjdk.jmh.annotations._
import shapeless.HNil

@State(Scope.Benchmark)
class MatcherBenchmark extends FinchBenchmark {

  val empty: Input = Input(Request())
  val fooBarBaz: Input = Input(Request("/foo/bar/baz"))

  @Benchmark
  def stringSome: Option[HNil] = "foo".apply(fooBarBaz).value

  @Benchmark
  def stringNone: Option[HNil] = "foo".apply(empty).value
}
