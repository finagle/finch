package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class OptionalParamSpec extends FlatSpec with Matchers {

  "An OptionalParam" should "be properly parsed when it exists" in {
    val request: Request = Request(("foo", "5"))
    val futureResult: Future[Option[String]] = paramOption("foo")(request)
    Await.result(futureResult) shouldBe Some("5")
  }

  it should "return none if the param is not present" in {
    val request: Request = Request()
    val futureResult: Future[Option[String]] = paramOption("foo")(request)
    Await.result(futureResult) shouldBe None
  }

  it should "return none if the param is empty" in {
    val request: Request = Request("foo" -> "")
    val futureResult: Future[Option[String]] = paramOption("foo")(request)
    Await.result(futureResult) shouldBe None
  }

  it should "return provided default value if the param is empty" in {
    val request: Request = Request()
    val futureResult: Future[String] = paramOption("foo").withDefault("bar")(request)
    Await.result(futureResult) shouldBe "bar"
  }

  it should "return provided alternative option if the param is empty" in {
    val request: Request = Request()
    val futureResult: Future[Option[String]] = paramOption("foo").orElse(Some("bar"))(request)
    Await.result(futureResult) shouldBe Some("bar")
  }

  it should "return existing value even if default value is provided" in {
    val request: Request = Request("foo" -> "baz")
    val futureResult: Future[String] = paramOption("foo").withDefault("bar")(request)
    Await.result(futureResult) shouldBe "baz"
  }

  it should "have a matching RequestItem" in {
    val param = "foo"
    paramOption(param).item shouldBe items.ParamItem(param)
  }


  "An OptionalBooleanParam" should "be parsed as an integer" in {
    val request: Request = Request(("foo", "true"))
    val futureResult: Future[Option[Boolean]] = paramOption("foo").as[Boolean].apply(request)
    Await.result(futureResult) shouldBe Some(true)
  }

  it should "produce an error if the param is not a number" in {
    val request: Request = Request(("foo", "non-boolean"))
    val futureResult: Future[Option[Boolean]] = paramOption("foo").as[Boolean].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }


  "An OptionalIntParam" should "be parsed as an integer" in {
    val request: Request = Request(("foo", "5"))
    val futureResult: Future[Option[Int]] = paramOption("foo").as[Int].apply(request)
    Await.result(futureResult) shouldBe Some(5)
  }

  it should "produce an error if the param is not a number" in {
    val request: Request = Request(("foo", "non-number"))
    val futureResult: Future[Option[Int]] = paramOption("foo").as[Int].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }


  "An OptionalLongParam" should "be parsed as a long" in {
    val request: Request = Request(("foo", "9000000000000000"))
    val futureResult: Future[Option[Long]] = paramOption("foo").as[Long].apply(request)
    Await.result(futureResult) shouldBe Some(9000000000000000L)
  }

  it should "produce an error if the param is not a number" in {
    val request: Request = Request(("foo", "non-number"))
    val futureResult: Future[Option[Long]] = paramOption("foo").as[Long].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }

  "An OptionalFloatParam" should "be parsed as a double" in {
    val request: Request = Request(("foo", "5.123"))
    val futureResult: Future[Option[Float]] = paramOption("foo").as[Float].apply(request)
    Await.result(futureResult) shouldBe Some(5.123f)
  }

  it should "produce an error if the param is not a number" in {
    val request: Request = Request(("foo", "non-number"))
    val futureResult: Future[Option[Float]] = paramOption("foo").as[Float].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }

  "An OptionalDoubleParam" should "be parsed as a float" in {
    val request: Request = Request(("foo", "100.0"))
    val futureResult: Future[Option[Double]] = paramOption("foo").as[Double].apply(request)
    Await.result(futureResult) shouldBe Some(100.0)
  }

  it should "produce an error if the param is not a number" in {
    val request: Request = Request(("foo", "non-number"))
    val futureResult: Future[Option[Double]] = paramOption("foo").as[Double].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }
}
