package io.finch

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.IO
import cats.laws.discipline.AlternativeTests
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import com.twitter.conversions.time._
import com.twitter.finagle.http.{Cookie, Method, Request}
import com.twitter.util.{Return, Throw}
import io.finch.data.Foo
import shapeless._

class EndpointSpec extends FinchSpec {

  type EndpointIO[A] = Endpoint[IO, A]

  implicit val isomorphisms: Isomorphisms[EndpointIO] =
    Isomorphisms.invariant[EndpointIO](Endpoint.endpointInstances)

  checkAll("Endpoint[String]", AlternativeTests[EndpointIO].applicative[String, String, String])

  checkAll("ExtractPath[String]", ExtractPathLaws[IO, String].all)
  checkAll("ExtractPath[Int]", ExtractPathLaws[IO, Int].all)
  checkAll("ExtractPath[Long]", ExtractPathLaws[IO, Long].all)
  checkAll("ExtractPath[UUID]", ExtractPathLaws[IO, UUID].all)
  checkAll("ExtractPath[Boolean]", ExtractPathLaws[IO, Boolean].all)

  behavior of "Endpoint"

  private[this] val emptyRequest = Request()

  it should "support very basic map" in {
    check { i: Input =>
      path[String].map(_ * 2).apply(i).awaitValueUnsafe() === i.route.headOption.map(_ * 2)
    }
  }

  it should "support transform" in {
    check { i: Input =>
      val fn = (fs: IO[Output[String]]) => fs.map(_.map(_ * 2))
      path[String].transform(fn).apply(i).awaitValueUnsafe() === i.route.headOption.map(_ * 2)
    }
  }

  it should "propagate the default (Ok) output" in {
    check { i: Input =>
      path[String].apply(i).awaitOutputUnsafe() === i.route.headOption.map(s => Ok(s))
    }
  }

  it should "propagate the default (Ok) output through its map'd/mapAsync'd version" in {
    check { i: Input =>
      val expected = i.route.headOption.map(s => Ok(s.length))

      path[String].map(s => s.length).apply(i).awaitOutputUnsafe() === expected &&
      path[String].mapAsync(s => IO.pure(s.length)).apply(i).awaitOutputUnsafe() === expected
    }
  }

  it should "propagate the output through mapOutputAsync and /" in {
    def expected(i: Int): Output[Int] =
      Created(i)
        .withHeader("A" -> "B")
        .withCookie(new Cookie("C", "D"))

    check { i: Input =>
      path[String].mapOutputAsync(s => IO.pure(expected(s.length))).apply(i).awaitOutputUnsafe() ===
        i.route.headOption.map(s => expected(s.length))
    }

    check { i: Input =>
      val e = i.route.dropRight(1)
        .map(s => path(s))
        .foldLeft[Endpoint[IO, HNil]](zero)((acc, ee) => acc :: ee)

      val v = (e :: path[String]).mapOutputAsync(s => IO.pure(expected(s.length))).apply(i)
      v.awaitOutputUnsafe() === i.route.lastOption.map(s => expected(s.length))
    }
  }

  it should "match one patch segment" in {
    check { i: Input =>
      val v = i.route.headOption
        .flatMap(s => path(s).apply(i).remainder)

      v.isEmpty|| v === Some(i.withRoute(i.route.tail))
    }
  }

  it should "always match the entire input with *" in {
    check { i: Input =>
      pathAny.apply(i).remainder === Some(i.copy(route = Nil))
    }
  }

  it should "match the HTTP method" in {
    def matchMethod(
        m: Method,
        f: Endpoint[IO, HNil] => Endpoint[IO, HNil]): Input => Boolean = { i: Input =>

      val v = f(zero)(i)
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
    check(matchMethod(Method.Delete, delete))
  }

  it should "always match the identity instance" in {
    check { i: Input =>
      zero.apply(i).remainder === Some(i)
    }
  }

  it should "match the entire input" in {
    check { i: Input =>
      val e = i.route.map(s => path(s)).foldLeft[Endpoint[IO, HNil]](zero)((acc, e) => acc :: e)
      e(i).remainder === Some(i.copy(route = Nil))
    }
  }

  it should "not match the entire input if one of the underlying endpoints is failed" in {
    check { (i: Input, s: String) =>
      (pathAny :: s).apply(i).remainder === None
    }
  }

  it should "match the input if one of the endpoints succeed" in {
    def matchOneOfTwo(f: String => Endpoint[IO, HNil]): Input => Boolean = { i: Input =>
      val v = i.route.headOption.map(f).flatMap(e => e(i).remainder)
      v.isEmpty || v === Some(i.withRoute(i.route.tail))
    }

    check(matchOneOfTwo(s => path(s).coproduct(path(s.reverse))))
    check(matchOneOfTwo(s => path(s.reverse).coproduct(path(s))))
  }

  it should "have the correct string representation" in {
    def standaloneMatcher[A]: A => Boolean = { a: A =>
      path(a.toString).toString == a.toString
    }

    check(standaloneMatcher[String])
    check(standaloneMatcher[Int])
    check(standaloneMatcher[Boolean])

    def methodMatcher(
        m: Method,
        f: Endpoint[IO, HNil] => Endpoint[IO, HNil]
      ): String => Boolean = { s: String => f(s).toString === m.toString.toUpperCase + " /" + s }

    check(methodMatcher(Method.Get, get))
    check(methodMatcher(Method.Post, post))
    check(methodMatcher(Method.Trace, trace))
    check(methodMatcher(Method.Put, put))
    check(methodMatcher(Method.Patch, patch))
    check(methodMatcher(Method.Head, head))
    check(methodMatcher(Method.Options, options))
    check(methodMatcher(Method.Delete, delete))

    check { (s: String, i: Int) => path(s).map(_ => i).toString === s }
    check { (s: String, t: String) => (path(s) :+: path(t)).toString === s"($s :+: $t)" }
    check { (s: String, t: String) => (path(s) :: path(t)).toString === s"$s :: $t" }
    check { s: String => path(s).product[String](pathAny.map(_ => "foo")).toString === s }
    check { (s: String, t: String) => path(s).mapAsync(_ => IO.pure(t)).toString === s }

    zero.toString shouldBe ""
    pathAny.toString shouldBe "*"
    path[Int].toString shouldBe ":int"
    path[String].toString shouldBe ":string"
    path[Long].toString shouldBe ":long"
    path[UUID].toString shouldBe ":uuid"
    path[Boolean].toString shouldBe ":boolean"

    paths[Int].toString shouldBe ":int*"
    paths[String].toString shouldBe ":string*"
    paths[Long].toString shouldBe ":long*"
    paths[UUID].toString shouldBe ":uuid*"
    paths[Boolean].toString shouldBe ":boolean*"

    (path[Int] :: path[String]).toString shouldBe ":int :: :string"
    (path[Boolean] :+: path[Long]).toString shouldBe "(:boolean :+: :long)"
  }

  it should "always respond with the same output if it's a constant Endpoint" in {
    check { s: String =>
      const(s).apply(Input.get("/")).awaitValueUnsafe() === Some(s) &&
      lift(s).apply(Input.get("/")).awaitValueUnsafe() === Some(s) &&
      liftAsync(IO.pure(s)).apply(Input.get("/")).awaitValueUnsafe() === Some(s)
    }

    check { o: Output[String] =>
      liftOutput(o).apply(Input.get("/")).awaitOutputUnsafe() === Some(o) &&
      liftOutputAsync(IO.pure(o)).apply(Input.get("/")).awaitOutputUnsafe() === Some(o)
    }
  }

  it should "support the as[A] method for HList" in {
    case class Foo(s: String, i: Int, b: Boolean)

    val foo = (path[String] :: path[Int] :: path[Boolean]).as[Foo]

    check { (s: String, i: Int, b: Boolean) =>
      foo(Input(emptyRequest, Seq(s, i.toString, b.toString))).awaitValueUnsafe() ===
        Some(Foo(s, i, b))
    }
  }

  it should "rescue the exception occurred in it" in {
    check { (i: Input, s: String, e: Exception) =>
      val result = liftAsync[String](IO.raiseError(e)).handle {
        case _ => Created(s)
      }.apply(i).awaitOutput()
      result === Some(Return(Created(s)))
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
      multipartFileUpload("foo").map(_.fileName), paramsNel("foo").map(_.toList.mkString),
      paramsNel("foor").map(_.toList.mkString), binaryBody.map(new String(_)), stringBody
    ).foreach { ii => ii(i).awaitValue() shouldBe Some(Throw(Error.NotPresent(ii.item))) }
  }

  it should "maps lazily to values" in {
    val i = Input(emptyRequest, Seq.empty)
    var c = 0
    val e = get(pathAny) { c = c + 1; Ok(c) }

    e(i).awaitValueUnsafe() shouldBe Some(1)
    e(i).awaitValueUnsafe() shouldBe Some(2)
  }

  it should "not evaluate Futures until matched" in {
    val i = Input(emptyRequest, Seq("a", "10"))
    var flag = false

    val endpointWithFailedFuture = "a".mapAsync { nil =>
      IO { flag = true; nil }
    }

    val e = ("a" :: "10") :+: endpointWithFailedFuture
    e(i).isMatched shouldBe true
    flag shouldBe false
  }

  it should "be greedy in terms of | compositor" in {
    val a = Input(emptyRequest, Seq("a", "10"))
    val b = Input(emptyRequest, Seq("a"))

    val e1 = "a".coproduct("b").coproduct("a" :: "10")
    val e2 = ("a" :: "10").coproduct("b").coproduct("a")

    e1(a).remainder shouldBe Some(a.withRoute(a.route.drop(2)))
    e1(b).remainder shouldBe Some(b.withRoute(b.route.drop(2)))
    e2(a).remainder shouldBe Some(a.withRoute(a.route.drop(2)))
    e2(b).remainder shouldBe Some(b.withRoute(b.route.drop(2)))
  }

  it should "accumulate errors on its product" in {
    check { (a: Either[Error, Errors], b: Either[Error, Errors]) =>
      val aa = a.fold[Exception](identity, identity)
      val bb = b.fold[Exception](identity, identity)

      val left = liftAsync[Unit](IO.raiseError(aa))
      val right = liftAsync[Unit](IO.raiseError(bb))

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
      val aa = liftAsync[Unit](IO.raiseError(a))
      val bb = liftAsync[Unit](IO.raiseError(b))
      val ee = liftAsync[Unit](IO.raiseError(e))

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

  it should "accumulate EndpointResult.NotMatched in its | compositor" in {
    val a = get("foo")
    val b = post("foo")
    val ab = a.coproduct(b)

    ab(Input.get("/foo")).isMatched shouldBe true
    ab(Input.post("/foo")).isMatched shouldBe true

    val put = ab(Input.put("/foo"))
    put.isMatched shouldBe false
    put.asInstanceOf[EndpointResult.NotMatched.MethodNotAllowed[IO]].allowed.toSet shouldBe {
      Set(Method.Post, Method.Get)
    }
  }

  it should "support the as[A] method on Endpoint[Seq[String]]" in {
    val foos = params[Foo]("testEndpoint")
    foos(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe() shouldBe Some(Seq(Foo("a")))
  }

  it should "liftToTry" in {
    check { e: Endpoint[IO, Unit] =>
      val i = Input.get("/")
      e(i).awaitValue() === e.liftToTry.apply(i).awaitValueUnsafe()
    }
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint = params[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }

  it should "support the as[A] method on Endpoint[NonEmptyList[A]]" in {
    val foos = paramsNel[Foo]("testEndpoint")
    foos(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe() shouldBe
      Some(NonEmptyList.of(Foo("a")))
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint = paramsNel[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe(10.seconds)
    )
  }
}
