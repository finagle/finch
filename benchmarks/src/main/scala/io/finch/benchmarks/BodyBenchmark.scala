package io.finch.benchmarks

import com.twitter.io.Buf
import io.finch._
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}

@State(Scope.Benchmark)
class BodyBenchmark extends FinchBenchmark {

  val input = Input.post("/").withBody[Text.Plain](Buf.Utf8("x" * 1024))

  @Benchmark
  def bufOption: Option[Buf] = bodyOption(input).value.get

  @Benchmark
  def buf: Buf = body(input).value.get

  @Benchmark
  def stringOption: Option[String] = bodyStringOption(input).value.get

  @Benchmark
  def string: String = bodyString(input).value.get

  @Benchmark
  def byteArrayOption: Option[Array[Byte]] = bodyByteArrayOption(input).value.get

  @Benchmark
  def byteArray: Array[Byte] = bodyByteArray(input).value.get
}
