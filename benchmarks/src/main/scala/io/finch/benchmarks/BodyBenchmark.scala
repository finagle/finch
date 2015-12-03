package io.finch.benchmarks

import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import com.twitter.util.Await
import io.finch._
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}

@State(Scope.Benchmark)
class BodyBenchmark extends FinchBenchmark {

  val req: Request = {
    val r = Request()
    val content = Buf.Utf8("x" * 1024)
    r.content = content
    r.contentLength = content.length.toLong

    r
  }

  @Benchmark
  def stringOption: Option[String] = Await.result(bodyOption(req))

  @Benchmark
  def string: String = Await.result(body(req))

  @Benchmark
  def byteArrayOption: Option[Array[Byte]] = Await.result(binaryBodyOption(req))

  @Benchmark
  def byteArray: Array[Byte] = Await.result(binaryBody(req))
}
