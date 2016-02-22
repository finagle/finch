package io.finch

import java.util.UUID

import com.twitter.finagle.http.{Request, Method, Cookie}
import com.twitter.util.{Throw, Try, Future}

class EndpointSpec extends FinchSpec {

  behavior of "Endpoint"

  it should "extract one path segment" in {
    def extractOne[A](e: Endpoint[A], f: String => A): Input => Boolean = { i: Input =>
      val o = e(i)
      val v = i.headOption.flatMap(s => Try(f(s)).toOption)

      o.value === v && (v.isEmpty || o.remainder === Some(i.drop(1)))
    }

    check(extractOne(string, identity))
    check(extractOne(int, _.toInt))
    check(extractOne(boolean, _.toBoolean))
    check(extractOne(long, _.toLong))
    check(extractOne(uuid, UUID.fromString))
  }

  it should "extract tail of the path" in {
    def extractTail[A](e: Endpoint[Seq[A]]): Seq[A] => Boolean = { s: Seq[A] =>
      val i = Input(null, s.map(_.toString))
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
      string.map(_ * 2)(i).value === i.headOption.map(_ * 2)
    }
  }

  it should "propagate the default (Ok) output" in {
    check { i: Input =>
      string(i).output === i.headOption.map(s => Ok(s))
    }
  }

  it should "propagate the default (Ok) output through its map'd/mapAsync'd/ap'd version" in {
    check { i: Input =>
      val expected = i.headOption.map(s => Ok(s.length))
      string.map(s => s.length)(i).output === expected &&
      string.mapAsync(s => Future.value(s.length))(i).output === expected &&
      string.ap[Int](/.map(_ => s => s.length))(i).output == expected
    }
  }

  it should "propagate the output through mapOutputAsync and /" in {
    def expected(i: Int): Output[Int] =
      Created(i)
        .withHeader("A" -> "B")
        .withCookie(new Cookie("C", "D"))

    check { i: Input =>
      string.mapOutputAsync(s => Future.value(expected(s.length)))(i).output ===
        i.headOption.map(s => expected(s.length))
    }

    check { i: Input =>
      val e = i.path.dropRight(1)
        .map(s => s: Endpoint0)
        .foldLeft[Endpoint0](/)((acc, ee) => acc :: ee)

      val v = (e :: string).mapOutputAsync(s => Future.value(expected(s.length)))(i)
      v.output === i.path.lastOption.map(s => expected(s.length))
    }
  }

  it should "match one patch segment" in {
    def matchOne[A](f: String => A)(implicit ev: A => Endpoint0): Input => Boolean = { i: Input =>
      val v = i.path.headOption.flatMap(s => Try(f(s)).toOption).map(ev).flatMap(e => e(i))
      v.isEmpty || v.remainder === Some(i.drop(1))
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
      e(i).remainder == Some(i.copy(path = Nil))
    }
  }

  it should "not match the entire input if one of the underlying endpoints is failed" in {
    check { (i: Input, s: String) =>
      (* :: s).apply(i).remainder === None
    }
  }

  it should "match the input if one of the endpoints succeed" in {
    def matchOneOfTwo(f: String => Endpoint0): Input => Boolean = { i: Input =>
      val v = i.path.headOption.map(f).flatMap(e => e(i))
      v.isEmpty || v.remainder === Some(i.drop(1))
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
    check { (s: String, t: String) => ((s: Endpoint0) :: (t: Endpoint0)).toString === s"$s/$t" }
    check { s: String => (s: Endpoint0).ap[String](*.map(_ => _ => "foo")).toString === s }
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

    (int :: string).toString shouldBe ":int/:string"
    (boolean :+: long).toString shouldBe "(:boolean|:long)"
  }

  it should "always respond with the same output if it's a constant Endpoint" in {
    check { (i: Input, s: String) =>
      val expected = Ok(s)
      Endpoint(expected)(i).output === Some(expected)
    }
  }

  it should "support the as[A] method" in {
    case class Foo(s: String, i: Int, b: Boolean)
    val foo = (string :: int :: boolean).as[Foo]
    check { (s: String, i: Int, b: Boolean) =>
      foo(Input(null, Seq(s, i.toString, b.toString))).value === Some(Foo(s, i, b))
    }
  }

  it should "rescue the exception occurred in it" in {
    check { (i: Input, s: String, e: Exception) =>
      Endpoint(Ok(Future.exception(e)))
        .handle({ case _ => Created(s) })(i)
        .output === Some(Created(s))
    }
  }

  it should "throw NotPresent if an item is not found" in {
    val i = Input(Request())

    Seq(
      param("foo"), header("foo"), body, cookie("foo").map(_.value),
      fileUpload("foo").map(_.fileName), paramsNonEmpty("foo").map(_.mkString),
      binaryBody.map(new String(_))
    ).foreach { ii => ii(i).poll shouldBe Some(Throw(Error.NotPresent(ii.item))) }
  }

  it should "maps lazily to values" in {
    val i = Input(null, Seq.empty)
    var c = 0
    val e = * { c = c + 1; Ok(c) }

    e(i).value shouldBe Some(1)
    e(i).value shouldBe Some(2)
  }

  it should "not evaluate Futures until matched" in {
    val i = Input(null, Seq("a", "10"))
    var flag = false

    val endpointWithFailedFuture = "a".mapAsync { nil =>
      Future { flag = true; nil }
    }

    val e = ("a" :: 10) | endpointWithFailedFuture
    e(i).isDefined shouldBe true
    flag shouldBe false
  }

  it should "be greedy in terms of | compositor" in {
    val a = Input(null, Seq("a", "10"))
    val b = Input(null, Seq("a"))

    val e1: Endpoint0 = "a" | "b" | ("a" :: 10)
    val e2: Endpoint0 = ("a" :: 10) | "b" |  "a"

    e1(a).remainder shouldBe Some(a.drop(2))
    e1(b).remainder shouldBe Some(b.drop(2))
    e2(a).remainder shouldBe Some(a.drop(2))
    e2(b).remainder shouldBe Some(b.drop(2))
  }
}
