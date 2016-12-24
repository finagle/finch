package io.finch

import com.twitter.io.Buf
import com.twitter.util.Future
import io.finch.data.Foo
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import shapeless._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
abstract class FinchBenchmark {
  val postPayload: Input = Input.post("/").withBody[Text.Plain](Buf.Utf8("x" * 1024))
  val getRoot: Input = Input.get("/")
  val getFooBarBaz: Input = Input.get("/foo/bar/baz")
  val getTenTwenty: Input = Input.get("/10/20")
  val getTrueFalse: Input = Input.get("/true/false")
}

@State(Scope.Benchmark)
class BodyBenchmark extends FinchBenchmark {

  val fooOptionAsText: Endpoint[Option[Foo]] = bodyOption[Foo, Text.Plain]
  val fooAsText: Endpoint[Foo] = body[Foo, Text.Plain]

  @Benchmark
  def fooOption: Option[Option[Foo]] = fooOptionAsText(postPayload).value

  @Benchmark
  def foo: Option[Foo] = fooAsText(postPayload).value

  @Benchmark
  def stringOption: Option[Option[String]] = stringBodyOption(postPayload).value

  @Benchmark
  def string: Option[String] = stringBody(postPayload).value

  @Benchmark
  def byteArrayOption: Option[Option[Array[Byte]]] = binaryBodyOption(postPayload).value

  @Benchmark
  def byteArray: Option[Array[Byte]] = binaryBody(postPayload).value
}

@State(Scope.Benchmark)
class MatchPathBenchmark extends FinchBenchmark {

  val foo: Endpoint[HNil] = "foo"

  @Benchmark
  def stringSome: Option[HNil] = foo(getFooBarBaz).value

  @Benchmark
  def stringNone: Option[HNil] = foo(getRoot).value
}

@State(Scope.Benchmark)
class ExtractPathBenchmark extends FinchBenchmark {
  @Benchmark
  def stringSome: Option[String] = string(getFooBarBaz).value

  @Benchmark
  def stringNone: Option[String] = string(getRoot).value

  @Benchmark
  def intSome: Option[Int] = int(getTenTwenty).value

  @Benchmark
  def intNone: Option[Int] = int(getFooBarBaz).value

  @Benchmark
  def booleanSome: Option[Boolean] = boolean(getTrueFalse).value

  @Benchmark
  def booleanNone: Option[Boolean] = boolean(getTenTwenty).value
}

@State(Scope.Benchmark)
class ProductBenchmark extends FinchBenchmark {
  val both: Endpoint[(Int, String)] = Endpoint.const(42).product(Endpoint.const("foo"))
  val left: Endpoint[(Int, String)] = Endpoint.const(42).product(Endpoint.empty[String])
  val right: Endpoint[(Int, String)] = Endpoint.empty[Int].product(Endpoint.const("foo"))

  @Benchmark
  def bothMatched: Option[(Int, String)] = both(getRoot).value

  @Benchmark
  def leftMatched: Option[(Int, String)] = left(getRoot).value

  @Benchmark
  def rightMatched: Option[(Int, String)] = right(getRoot).value
}

@State(Scope.Benchmark)
class CoproductBenchmark extends FinchBenchmark {
  val both: Endpoint[String] = Endpoint.const("foo") | Endpoint.const("bar")
  val left: Endpoint[String] = Endpoint.const("foo") | Endpoint.empty[String]
  val right: Endpoint[String] = Endpoint.empty[String] | Endpoint.const("bar")

  @Benchmark
  def bothMatched: Option[String] = both(getRoot).value

  @Benchmark
  def leftMatched: Option[String] = left(getRoot).value

  @Benchmark
  def rightMatched: Option[String] = right(getRoot).value
}

@State(Scope.Benchmark)
class MapBenchmark extends FinchBenchmark {
  val ten: Endpoint[Int] = Endpoint.const(10)
  val mapTen: Endpoint[Int] = ten.map(a => a + 20)
  val mapTenAsync: Endpoint[Int] = ten.mapAsync(a => Future.value(a + 20))
  val mapTenOutput: Endpoint[Int] = ten.mapOutput(a => Ok(a + 10))
  val mapTenOutputAsync: Endpoint[Int] = ten.mapOutputAsync(a => Future.value(Ok(a + 10)))

  @Benchmark
  def map: Option[Int] = mapTen(getRoot).value

  @Benchmark
  def mapAsync: Option[Int] = mapTenAsync(getRoot).value

  @Benchmark
  def mapOutput: Option[Int] = mapTenOutput(getRoot).value

  @Benchmark
  def mapOutputAsync: Option[Int] = mapTenOutputAsync(getRoot).value
}
