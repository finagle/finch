package io.finch.benchmarks

import io.finch._
import org.openjdk.jmh.annotations._
import shapeless.HNil

@State(Scope.Benchmark)
class MatcherBenchmark extends FinchBenchmark {

  val empty: Input = Input.get("/")
  val fooBarBaz: Input = Input.get("/foo/bar/baz")

  val foo: Endpoint0 = "foo"

  @Benchmark
  def stringSome: Option[HNil] = foo(fooBarBaz).value

  @Benchmark
  def stringNone: Option[HNil] = foo(empty).value
}
