package io.finch

import java.util.UUID
import scala.reflect.ClassTag

import cats.data.NonEmptyList
import cats.laws.discipline.AlternativeTests
import com.twitter.conversions.time._
import com.twitter.finagle.http.{Cookie, Method, Request}
import com.twitter.io.Buf
import com.twitter.util.{Future, Throw, Try}
import io.finch.data.Foo
import io.finch.items.BodyItem

class EndpointSpec extends FinchSpec {
  checkAll("Endpoint[String]", AlternativeTests[Endpoint].applicative[String, String, String])

  behavior of "Endpoint"

  private[this] val emptyRequest = Request()

  it should "extract one path segment" in {
    def extractOne[A](e: Endpoint[A], f: String => A): Input => Boolean = { i: Input =>
      val o = e(i)
      val v = i.headOption.flatMap(s => Try(f(s)).toOption)

      o.awaitValueUnsafe() === v && (v.isEmpty || o.remainder === Some(i.drop(1)))
    }

    check(extractOne(string, identity))
    check(extractOne(int, _.toInt))
    check(extractOne(boolean, _.toBoolean))
    check(extractOne(long, _.toLong))
    check(extractOne(uuid, UUID.fromString))
  }

  it should "extract tail of the path" in {
    def extractTail[A](e: Endpoint[Seq[A]]): Seq[A] => Boolean = { s: Seq[A] =>
      val i = Input(emptyRequest, s.map(_.toString))
      e(i).remainder === Some(i.copy(path = Nil))
    }

    check(extractTail(strings))
    check(extractTail(ints))
    check(extractTail(booleans))
    check(extractTail(longs))
    check(extractTail(uuids))
  }

  it should "support very basic map" in {
    check { i: Input =>
      string.map(_ * 2)(i).awaitValueUnsafe() === i.headOption.map(_ * 2)
    }
  }

  it should "support transform" in {
    check { i: Input =>
      val fn = (fs: Future[Output[String]]) => fs.map(_.map(_ * 2))
      string.transform(fn)(i).awaitValueUnsafe() === i.headOption.map(_ * 2)
    }
  }

  it should "propagate the default (Ok) output" in {
    check { i: Input =>
      string(i).awaitOutputUnsafe() === i.headOption.map(s => Ok(s))
    }
  }

  it should "propagate the default (Ok) output through its map'd/mapAsync'd version" in {
    check { i: Input =>
      val expected = i.headOption.map(s => Ok(s.length))

      string.map(s => s.length)(i).awaitOutputUnsafe() === expected &&
      string.mapAsync(s => Future.value(s.length))(i).awaitOutputUnsafe() === expected
    }
  }

  it should "propagate the output through mapOutputAsync and /" in {
    def expected(i: Int): Output[Int] =
      Created(i)
        .withHeader("A" -> "B")
        .withCookie(new Cookie("C", "D"))

    check { i: Input =>
      string.mapOutputAsync(s => Future.value(expected(s.length)))(i).awaitOutputUnsafe() ===
        i.headOption.map(s => expected(s.length))
    }

    check { i: Input =>
      val e = i.path.dropRight(1)
        .map(s => s: Endpoint0)
        .foldLeft[Endpoint0](/)((acc, ee) => acc :: ee)

      val v = (e :: string).mapOutputAsync(s => Future.value(expected(s.length)))(i)
      v.awaitOutputUnsafe() === i.path.lastOption.map(s => expected(s.length))
    }
  }

  it should "match one patch segment" in {
    def matchOne[A](f: String => A)(implicit ev: A => Endpoint0): Input => Boolean = { i: Input =>
      val v = i.path.headOption
        .flatMap(s => Try(f(s)).toOption)
        .map(ev)
        .flatMap(e => e(i).remainder)

      v.isEmpty|| v === Some(i.drop(1))
    }

    check(matchOne(identity))
    check(matchOne(_.toInt))
    check(matchOne(_.toBoolean))
  }

  it should "always match the entire input with *" in {
    check { i: Input =>
      *(i).remainder === Some(i.copy(path = Nil))
    }
  }

  it should "match the HTTP method" in {
    def matchMethod(m: Method, f: Endpoint0 => Endpoint0): Input => Boolean = { i: Input =>
      val v = f(/)(i)
      (i.request.method === m && v.remainder === Some(i)) ||
      (i.request.method != m && v.remainder === None)
    }

    check(matchMethod(Method.Get, get))
    check(matchMethod(Method.Post, post))
    check(matchMethod(Method.Trace, trace))
    check(matchMethod(Method.Put, put))
    check(matchMethod(Method.Patch, patch))
    check(matchMethod(Method.Head, head))
    check(matchMethod(Method.Options, options))
    check(matchMethod(Method.Connect, connect))
    check(matchMethod(Method.Delete, delete))
  }

  it should "always match the identity instance" in {
    check { i: Input =>
      /(i).remainder === Some(i)
    }
  }

  it should "match the entire input" in {
    check { i: Input =>
      val e = i.path.map(s => s: Endpoint0).foldLeft[Endpoint0](/)((acc, e) => acc :: e)
      e(i).remainder === Some(i.copy(path = Nil))
    }
  }

  it should "not match the entire input if one of the underlying endpoints is failed" in {
    check { (i: Input, s: String) =>
      (* :: s).apply(i).remainder === None
    }
  }

  it should "match the input if one of the endpoints succeed" in {
    def matchOneOfTwo(f: String => Endpoint0): Input => Boolean = { i: Input =>
      val v = i.path.headOption.map(f).flatMap(e => e(i).remainder)
      v.isEmpty || v === Some(i.drop(1))
    }

    check(matchOneOfTwo(s => (s: Endpoint0) | (s.reverse: Endpoint0)))
    check(matchOneOfTwo(s => (s.reverse: Endpoint0) | (s: Endpoint0)))
  }

  it should "have the correct string representation" in {
    def standaloneMatcher[A](implicit f: A => Endpoint0): A => Boolean = { a: A =>
      f(a).toString == a.toString
    }

    check(standaloneMatcher[String])
    check(standaloneMatcher[Int])
    check(standaloneMatcher[Boolean])

    def methodMatcher(m: Method, f: Endpoint0 => Endpoint0): String => Boolean = { s: String =>
      f(s).toString === m.toString().toUpperCase + " /" + s
    }

    check(methodMatcher(Method.Get, get))
    check(methodMatcher(Method.Post, post))
    check(methodMatcher(Method.Trace, trace))
    check(methodMatcher(Method.Put, put))
    check(methodMatcher(Method.Patch, patch))
    check(methodMatcher(Method.Head, head))
    check(methodMatcher(Method.Options, options))
    check(methodMatcher(Method.Connect, connect))
    check(methodMatcher(Method.Delete, delete))

    check { (s: String, i: Int) => (s: Endpoint0).map(_ => i).toString === s }
    check { (s: String, t: String) => ((s: Endpoint0) | (t: Endpoint0)).toString === s"($s|$t)" }
    check { (s: String, t: String) => ((s: Endpoint0) :: (t: Endpoint0)).toString === s"$s :: $t" }
    check { s: String => (s: Endpoint0).product[String](*.map(_ => "foo")).toString === s }
    check { (s: String, t: String) => (s: Endpoint0).mapAsync(_ => Future.value(t)).toString === s }

    *.toString shouldBe "*"
    /.toString shouldBe ""
    int.toString shouldBe ":int"
    string.toString shouldBe ":string"
    long.toString shouldBe ":long"
    uuid.toString shouldBe ":uuid"
    boolean.toString shouldBe ":boolean"

    ints.toString shouldBe ":int*"
    strings.toString shouldBe ":string*"
    longs.toString shouldBe ":long*"
    uuids.toString shouldBe ":uuid*"
    booleans.toString shouldBe ":boolean*"

    (int :: string).toString shouldBe ":int :: :string"
    (boolean :+: long).toString shouldBe "(:boolean|:long)"
  }

  it should "always respond with the same output if it's a constant Endpoint" in {
    check { s: String =>
      Endpoint.const(s)(Input.get("/")).awaitValueUnsafe() === Some(s) &&
      Endpoint.lift(s)(Input.get("/")).awaitValueUnsafe() === Some(s) &&
      Endpoint.liftFuture(Future.value(s))(Input.get("/")).awaitValueUnsafe() === Some(s)
    }

    check { o: Output[String] =>
      Endpoint.liftOutput(o)(Input.get("/")).awaitOutputUnsafe() === Some(o) &&
      Endpoint.liftFutureOutput(Future.value(o))(Input.get("/")).awaitOutputUnsafe() === Some(o)
    }
  }

  it should "support the as[A] method" in {
    case class Foo(s: String, i: Int, b: Boolean)

    val foo = (string :: int :: boolean).as[Foo]

    check { (s: String, i: Int, b: Boolean) =>
      foo(Input(emptyRequest, Seq(s, i.toString, b.toString))).awaitValueUnsafe() ===
        Some(Foo(s, i, b))
    }
  }

  it should "throw Error.NotParsed if as[A] method fails" in {
    val cause = new Exception("can't parse this")
    implicit val failingDecodeEntity: DecodeEntity[Foo] =
      DecodeEntity.instance(_ => Throw(cause))

    val foo = stringBody.as[Foo]
    val fooOption = stringBodyOption.as[Foo]
    val i = (s: String) => Input.post("/").withBody[Text.Plain](Buf.Utf8(s))

    check { (s: String) =>
      foo(i(s)).awaitValue() === Some(Throw(
        Error.NotParsed(BodyItem, implicitly[ClassTag[Foo]], cause)
      ))
    }

    check { (s: String) =>
      fooOption(i(s)).awaitValue() === Some(Throw(
        Error.NotParsed(BodyItem, implicitly[ClassTag[Foo]], cause)
      ))
    }
  }

  it should "rescue the exception occurred in it" in {
    check { (i: Input, s: String, e: Exception) =>
      Endpoint.liftFuture[Unit](Future.exception(e))
        .handle({ case _ => Created(s) })(i)
        .awaitOutputUnsafe() === Some(Created(s))
    }
  }

  it should "not split comma separated param values" in {
    val i = Input.get("/index", "foo" -> "a,b")
    val e = params("foo")
    e(i).awaitValueUnsafe() shouldBe Some(Seq("a,b"))
  }

  it should "throw NotPresent if an item is not found" in {
    val i = Input.get("/")

    Seq(
      param("foo"), header("foo"), cookie("foo").map(_.value),
      fileUpload("foo").map(_.fileName), paramsNel("foo").map(_.toList.mkString),
      paramsNel("foor").map(_.toList.mkString), binaryBody.map(new String(_)), stringBody
    ).foreach { ii => ii(i).awaitValue() shouldBe Some(Throw(Error.NotPresent(ii.item))) }
  }

  it should "maps lazily to values" in {
    val i = Input(emptyRequest, Seq.empty)
    var c = 0
    val e = * { c = c + 1; Ok(c) }

    e(i).awaitValueUnsafe() shouldBe Some(1)
    e(i).awaitValueUnsafe() shouldBe Some(2)
  }

  it should "not evaluate Futures until matched" in {
    val i = Input(emptyRequest, Seq("a", "10"))
    var flag = false

    val endpointWithFailedFuture = "a".mapAsync { nil =>
      Future { flag = true; nil }
    }

    val e = ("a" :: 10) | endpointWithFailedFuture
    e(i).isMatched shouldBe true
    flag shouldBe false
  }

  it should "be greedy in terms of | compositor" in {
    val a = Input(emptyRequest, Seq("a", "10"))
    val b = Input(emptyRequest, Seq("a"))

    val e1: Endpoint0 = "a" | "b" | ("a" :: 10)
    val e2: Endpoint0 = ("a" :: 10) | "b" |  "a"

    e1(a).remainder shouldBe Some(a.drop(2))
    e1(b).remainder shouldBe Some(b.drop(2))
    e2(a).remainder shouldBe Some(a.drop(2))
    e2(b).remainder shouldBe Some(b.drop(2))
  }

  it should "accumulate errors on its product" in {
    check { (a: Either[Error, Errors], b: Either[Error, Errors]) =>
      val aa = a.fold[Exception](identity, identity)
      val bb = b.fold[Exception](identity, identity)

      val left = Endpoint.liftFuture[Unit](Future.exception(aa))
      val right = Endpoint.liftFuture[Unit](Future.exception(bb))

      val lr = left.product(right)
      val rl = right.product(left)

      val all =
        a.fold[Set[Error]](e => Set(e), es => es.errors.toList.toSet) ++
        b.fold[Set[Error]](e => Set(e), es => es.errors.toList.toSet)

      val Some(Throw(first)) = lr(Input.get("/")).awaitValue()
      val Some(Throw(second)) = rl(Input.get("/")).awaitValue()

      first.asInstanceOf[Errors].errors.toList.toSet === all &&
      second.asInstanceOf[Errors].errors.toList.toSet === all
    }
  }

  it should "fail-fast with the first non-error observed" in {
    check { (a: Error, b: Errors, e: Exception) =>
      val aa = Endpoint.liftFuture[Unit](Future.exception(a))
      val bb = Endpoint.liftFuture[Unit](Future.exception(b))
      val ee = Endpoint.liftFuture[Unit](Future.exception(e))

      val aaee = aa.product(ee)
      val eeaa = ee.product(aa)

      val bbee = bb.product(ee)
      val eebb = ee.product(bb)

      aaee(Input.get("/")).awaitValue() === Some(Throw(e)) &&
      eeaa(Input.get("/")).awaitValue() === Some(Throw(e)) &&
      bbee(Input.get("/")).awaitValue() === Some(Throw(e)) &&
      eebb(Input.get("/")).awaitValue() === Some(Throw(e))
    }
  }

  it should "support the as[A] method on Endpoint[Seq[String]]" in {
    val foos = params("testEndpoint").as[Foo]
    foos(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe() shouldBe Some(Seq(Foo("a")))
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint: Endpoint[Seq[UUID]] = params("testEndpoint").as[UUID]
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }

  it should "support the as[A] method on Endpoint[NonEmptyList[A]]" in {
    val foos = paramsNel("testEndpoint").as[Foo]
    foos(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe() shouldBe
      Some(NonEmptyList.of(Foo("a")))
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint: Endpoint[NonEmptyList[UUID]] = paramsNel("testEndpoint").as[UUID]
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe(10.seconds)
    )
  }
}
