package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Try}
import io.finch.data.Foo
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.util.Random
import shapeless._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
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
  def fooOption: Option[Option[Foo]] = fooOptionAsText(postPayload).awaitValueUnsafe()

  @Benchmark
  def foo: Option[Foo] = fooAsText(postPayload).awaitValueUnsafe()

  @Benchmark
  def stringOption: Option[Option[String]] = stringBodyOption(postPayload).awaitValueUnsafe()

  @Benchmark
  def string: Option[String] = stringBody(postPayload).awaitValueUnsafe()

  @Benchmark
  def byteArrayOption: Option[Option[Array[Byte]]] = binaryBodyOption(postPayload).awaitValueUnsafe()

  @Benchmark
  def byteArray: Option[Array[Byte]] = binaryBody(postPayload).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class MatchPathBenchmark extends FinchBenchmark {

  val foo: Endpoint[HNil] = "foo"

  @Benchmark
  def stringSome: Option[HNil] = foo(getFooBarBaz).awaitValueUnsafe()

  @Benchmark
  def stringNone: Option[HNil] = foo(getRoot).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class ExtractPathBenchmark extends FinchBenchmark {
  @Benchmark
  def stringSome: Option[String] = path[String].apply(getFooBarBaz).awaitValueUnsafe()

  @Benchmark
  def stringNone: Option[String] = path[String].apply(getRoot).awaitValueUnsafe()

  @Benchmark
  def longSome: Option[Long] = path[Long].apply(getTenTwenty).awaitValueUnsafe()

  @Benchmark
  def longNone: Option[Long] = path[Long].apply(getFooBarBaz).awaitValueUnsafe()

  @Benchmark
  def intSome: Option[Int] = path[Int].apply(getTenTwenty).awaitValueUnsafe()

  @Benchmark
  def intNone: Option[Int] = path[Int].apply(getFooBarBaz).awaitValueUnsafe()

  @Benchmark
  def booleanSome: Option[Boolean] = path[Boolean].apply(getTrueFalse).awaitValueUnsafe()

  @Benchmark
  def booleanNone: Option[Boolean] = path[Boolean].apply(getTenTwenty).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class ProductBenchmark extends FinchBenchmark {
  val both: Endpoint[(Int, String)] = Endpoint.const(42).product(Endpoint.const("foo"))
  val left: Endpoint[(Int, String)] = Endpoint.const(42).product(Endpoint.empty[String])
  val right: Endpoint[(Int, String)] = Endpoint.empty[Int].product(Endpoint.const("foo"))

  @Benchmark
  def bothMatched: Option[(Int, String)] = both(getRoot).awaitValueUnsafe()

  @Benchmark
  def leftMatched: Option[(Int, String)] = left(getRoot).awaitValueUnsafe()

  @Benchmark
  def rightMatched: Option[(Int, String)] = right(getRoot).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class CoproductBenchmark extends FinchBenchmark {
  val both: Endpoint[String] = Endpoint.const("foo").coproduct(Endpoint.const("bar"))
  val left: Endpoint[String] = Endpoint.const("foo").coproduct(Endpoint.empty[String])
  val right: Endpoint[String] = Endpoint.empty[String].coproduct(Endpoint.const("bar"))

  @Benchmark
  def bothMatched: Option[String] = both(getRoot).awaitValueUnsafe()

  @Benchmark
  def leftMatched: Option[String] = left(getRoot).awaitValueUnsafe()

  @Benchmark
  def rightMatched: Option[String] = right(getRoot).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class MapBenchmark extends FinchBenchmark {
  val ten: Endpoint[Int] = Endpoint.const(10)
  val mapTen: Endpoint[Int] = ten.map(a => a + 20)
  val mapTenAsync: Endpoint[Int] = ten.mapAsync(a => Future.value(a + 20))
  val mapTenOutput: Endpoint[Int] = ten.mapOutput(a => Ok(a + 10))
  val mapTenOutputAsync: Endpoint[Int] = ten.mapOutputAsync(a => Future.value(Ok(a + 10)))

  @Benchmark
  def map: Option[Int] = mapTen(getRoot).awaitValueUnsafe()

  @Benchmark
  def mapAsync: Option[Int] = mapTenAsync(getRoot).awaitValueUnsafe()

  @Benchmark
  def mapOutput: Option[Int] = mapTenOutput(getRoot).awaitValueUnsafe()

  @Benchmark
  def mapOutputAsync: Option[Int] = mapTenOutputAsync(getRoot).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class JsonBenchmark extends FinchBenchmark {
  import io.circe.syntax._
  import io.circe.generic.auto._
  import io.finch.circe._

  val decodeFoo: Decode.Json[Foo] = Decode[Foo, Application.Json]
  val encodeFoo: Encode.Json[Foo] = Encode[Foo, Application.Json]

  val foo: Foo = Foo("x" * 1024)
  val buf: Buf = Buf.Utf8(foo.asJson.noSpaces)

  @Benchmark
  def decode: Try[Foo] = decodeFoo(buf, StandardCharsets.UTF_8)

  @Benchmark
  def encode: Buf = encodeFoo(foo, StandardCharsets.UTF_8)
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ToServiceBenchmark extends FinchBenchmark {
  import io.circe.generic.auto._
  import io.finch.circe._

  val fooService: Service[Request, Response] = Endpoint.const(
    List.fill(128)(Foo(scala.util.Random.alphanumeric.take(10).mkString))
  ).toService

  val intService: Service[Request, Response] = Endpoint.const(
    List.fill(128)(scala.util.Random.nextInt)
  ).toService

  @Benchmark
  def foos: Response = Await.result(fooService(Request()))

  @Benchmark
  def ints: Response = Await.result(intService(Request()))
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class BootstrapBenchmark extends FinchBenchmark {
  import io.circe.generic.auto._
  import io.finch.circe._

  implicit val encodeFoo: Encode.Aux[List[Foo], Text.Plain] =
    Encode.instance[List[Foo], Text.Plain]((a, _) => Buf.Utf8(a.toString))

  val endpoint: Endpoint[List[Foo]] =
    Endpoint.const(List.fill(128)(Foo(scala.util.Random.alphanumeric.take(10).mkString)))

  val singleType: Service[Request, Response] =
    Bootstrap.serve[Application.Json](endpoint).toService

  val negotiatable: Service[Request, Response] =
    Bootstrap
      .serve[Application.Json :+: Text.Plain :+: CNil](endpoint)
      .configure(negotiateContentType = true).toService

  @Benchmark
  def foos: Response = Await.result(singleType(Request()))

  @Benchmark
  def negotiation: Response = Await.result(negotiatable {
    val req = Request()
    req.accept = Random.shuffle("application/json" :: "text/plain" :: Nil).head
    req
  })

}

@State(Scope.Benchmark)
class TooFastStringBenchmark extends FinchBenchmark {

  import io.finch.internal.TooFastString

  @Benchmark
  def someBoolean: Option[Boolean] = "true".tooBoolean

  @Benchmark
  def someInt: Option[Int] = "12345".tooInt

  @Benchmark
  def someLong: Option[Long] = "12345678".tooLong
}
