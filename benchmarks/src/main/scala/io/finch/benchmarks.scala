package io.finch

import cats.effect.IO
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.util.{Await, Try}
import io.circe.generic.auto._
import io.finch.circe._
import io.finch.data.Foo
import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
import org.openjdk.jmh.annotations._
import shapeless._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
abstract class FinchBenchmark extends Endpoint.Module[IO] {
  val postPayload: Input = Input.post("/").withBody[Text.Plain](Buf.Utf8("x" * 1024))
  val getRoot: Input = Input.get("/")
  val getFooBarBaz: Input = Input.get("/foo/bar/baz")
  val getTenTwenty: Input = Input.get("/10/20")
  val getTrueFalse: Input = Input.get("/true/false")
}

@State(Scope.Benchmark)
class InputBenchmark extends FinchBenchmark {
  val foo = Request()
  val bar = Request("/foo/bar/baz/que/quz")

  @Benchmark
  def fromEmptyPath: Input = Input.fromRequest(foo)

  @Benchmark
  def fromPath: Input = Input.fromRequest(bar)
}

@State(Scope.Benchmark)
class BodyBenchmark extends FinchBenchmark {

  val fooOptionAsText: Endpoint[IO, Option[Foo]] = bodyOption[Foo, Text.Plain]
  val fooAsText: Endpoint[IO, Foo] = body[Foo, Text.Plain]

  @Benchmark
  def fooOption: Option[Option[Foo]] = fooOptionAsText(postPayload).awaitValueUnsafe()

  @Benchmark
  def foo: Option[Foo] = fooAsText(postPayload).awaitValueUnsafe()

  @Benchmark
  def stringOption: Option[Option[String]] = stringBodyOption.apply(postPayload).awaitValueUnsafe()

  @Benchmark
  def string: Option[String] = stringBody.apply(postPayload).awaitValueUnsafe()

  @Benchmark
  def byteArrayOption: Option[Option[Array[Byte]]] = binaryBodyOption.apply(postPayload).awaitValueUnsafe()

  @Benchmark
  def byteArray: Option[Array[Byte]] = binaryBody.apply(postPayload).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class MatchPathBenchmark extends FinchBenchmark {

  val foo: Endpoint[IO, HNil] = "foo"

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
  val both: Endpoint[IO, (Int, String)] =
    Endpoint[IO].const(42).product(Endpoint[IO].const("foo"))
  val left: Endpoint[IO, (Int, String)] =
    Endpoint[IO].const(42).product(Endpoint[IO].empty)
  val right: Endpoint[IO, (Int, String)] =
    Endpoint[IO].empty[Int].product(Endpoint[IO].const("foo"))

  @Benchmark
  def bothMatched: Option[(Int, String)] = both(getRoot).awaitValueUnsafe()

  @Benchmark
  def leftMatched: Option[(Int, String)] = left(getRoot).awaitValueUnsafe()

  @Benchmark
  def rightMatched: Option[(Int, String)] = right(getRoot).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class CoproductBenchmark extends FinchBenchmark {
  val both: Endpoint[IO, String] =
    Endpoint[IO].const("foo").coproduct(Endpoint[IO].const("bar"))
  val left: Endpoint[IO, String] =
    Endpoint[IO].const("foo").coproduct(Endpoint[IO].empty)
  val right: Endpoint[IO, String] =
    Endpoint[IO].empty.coproduct(Endpoint[IO].const("bar"))

  @Benchmark
  def bothMatched: Option[String] = both(getRoot).awaitValueUnsafe()

  @Benchmark
  def leftMatched: Option[String] = left(getRoot).awaitValueUnsafe()

  @Benchmark
  def rightMatched: Option[String] = right(getRoot).awaitValueUnsafe()
}

@State(Scope.Benchmark)
class MapBenchmark extends FinchBenchmark {
  val ten: Endpoint[IO, Int] = Endpoint[IO].const(10)
  val mapTen: Endpoint[IO, Int] = ten.map(a => a + 20)
  val mapTenAsync: Endpoint[IO, Int] = ten.mapAsync(a => IO.pure(a + 20))
  val mapTenOutput: Endpoint[IO, Int] = ten.mapOutput(a => Ok(a + 10))
  val mapTenOutputAsync: Endpoint[IO, Int] = ten.mapOutputAsync(a => IO.pure(Ok(a + 10)))

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
abstract class BootstrapBenchmark[CT](init: Bootstrap[HNil, HNil])(implicit
  tsf: ToService[Endpoint[IO, List[Foo]] :: HNil, CT :: HNil]
) extends FinchBenchmark {

  protected def issueRequest(): Request = Request()

  private val foo: Service[Request, Response] = init
    .serve[CT](Endpoint[IO].const(List.fill(128)(Foo(scala.util.Random.alphanumeric.take(10).mkString))))
    .toService

  @Benchmark
  def foos: Response = Await.result(foo(issueRequest()))
}

class JsonBootstrapBenchmark extends BootstrapBenchmark[Application.Json](Bootstrap)

class JsonNegotiatedBootstrapBenchmark extends BootstrapBenchmark[Application.Json](
    Bootstrap.configure(negotiateContentType = true))

class TextBootstrapBenchmark extends BootstrapBenchmark[Text.Plain](Bootstrap)

class TextNegotiatedBootstrapBenchmark extends BootstrapBenchmark[Text.Plain](
    Bootstrap.configure(negotiateContentType = true))

class JsonAndTextNegotiatedBootstrapBenchmark extends BootstrapBenchmark[Application.Json :+: Text.Plain :+: CNil](
    Bootstrap.configure(negotiateContentType = true)) {

  private val acceptValues: Array[String] = Array("application/json", "text/plain")

  override protected def issueRequest(): Request = {
    val req = Request()
    req.accept = acceptValues(ThreadLocalRandom.current().nextInt(acceptValues.length))

    req
  }
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

@State(Scope.Benchmark)
class HttpMessageBenchmark extends FinchBenchmark {

  import io.finch.internal.HttpMessage

  val req = Request()
  req.contentType = "application/json;charset=utf-8"

  @Benchmark
  def fastChartset: Charset = req.charsetOrUtf8

  @Benchmark
  def slowCharset: Charset = req.charset match {
    case Some(cs) => Charset.forName(cs)
    case None => StandardCharsets.UTF_8
  }
}
