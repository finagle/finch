package io.finch.benchmarks

import com.twitter.io.Buf
import io.finch._
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}

@State(Scope.Benchmark)
class BodyBenchmark extends FinchBenchmark {

  val input = Input.post("/").withBody[Text.Plain](Buf.Utf8("x" * 1024))

  @Benchmark
  def stringOption: Option[String] = bodyOption(input).value.get

  @Benchmark
  def string: String = body(input).value.get

  @Benchmark
  def byteArrayOption: Option[Array[Byte]] = binaryBodyOption(input).value.get

  @Benchmark
  def byteArray: Array[Byte] = binaryBody(input).value.get
}
