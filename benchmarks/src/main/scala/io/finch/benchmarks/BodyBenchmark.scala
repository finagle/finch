package io.finch.benchmarks

import com.twitter.io.Buf
import com.twitter.util.Return
import io.finch._
import io.finch.internal.BufText
import org.openjdk.jmh.annotations.{Benchmark, Scope, State}

@State(Scope.Benchmark)
class BodyBenchmark extends FinchBenchmark {

  implicit val decodeJsonAsString: Decode.Json[String] =
    Decode.json((b, cs) => Return(BufText.extract(b, cs)))

  val input = Input.post("/").withBody[Text.Plain](Buf.Utf8("x" * 1024))

  val bodyAsString = body.as[String]
  val bodyOptionAsString = bodyOption.as[String]

  val bodyAsString2 = body[String, Application.Json]
  val bodyOptionAsString2 = bodyOption[String, Application.Json]

  @Benchmark
  def jsonOption: Option[String] = bodyOptionAsString(input).value.get

  @Benchmark
  def json: String = bodyAsString(input).value.get

  @Benchmark
  def jsonOption2: Option[String] = bodyOptionAsString2(input).value.get

  @Benchmark
  def json2: String = bodyAsString2(input).value.get

  @Benchmark
  def stringOption: Option[String] = stringBodyOption(input).value.get

  @Benchmark
  def string: String = stringBody(input).value.get

  @Benchmark
  def byteArrayOption: Option[Array[Byte]] = binaryBodyOption(input).value.get

  @Benchmark
  def byteArray: Array[Byte] = binaryBody(input).value.get
}
