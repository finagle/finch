package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class OptionalParamsSpec extends FlatSpec with Matchers {

  "A OptionalParams" should "parse all of the url params with the same key" in {
    val request: Request = Request(("foo", "5"), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[Seq[String]] = params("foo")(request)
    val result: Seq[String] = Await.result(futureResult)
    result should have length 2
    result should contain("5")
    result should contain("25")
  }

  it should "return only params that are not the empty string" in {
    val request: Request = Request(("foo", ""), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[Seq[String]] = params("foo")(request)
    val result: Seq[String] = Await.result(futureResult)
    result should have length 1
    result should contain("25")
  }

  it should "return Nil if the parameter does not exist at all" in {
    val request: Request = Request(("foo", "5"), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[Seq[String]] = params("baz")(request)
    Await.result(futureResult) shouldBe Nil
  }

  it should "return Nil if all of the values for that parameter ar the emptry string" in {
    val request: Request = Request(("foo", ""), ("bar", "6"), ("foo", ""))
    val futureResult: Future[Seq[String]] = params("")(request)
    Await.result(futureResult) shouldBe Nil
  }

  it should "have a matching RequestItem" in {
    val param = "foo"
    params(param).item shouldBe items.ParamItem(param)
  }


  "A OptionalBooleanParams" should "be parsed as a list of booleans" in {
    val request: Request = Request(("foo", "true"), ("foo", "false"))
    val futureResult: Future[Seq[Boolean]] = params("foo").as[Boolean].apply(request)
    val result: Seq[Boolean] = Await.result(futureResult)
    result should have length 2
    result should contain(true)
    result should contain(false)
  }

  it should "produce an error if one of the params is not a boolean" in {
    val request: Request = Request(("foo", "true"), ("foo", "5"))
    val futureResult: Future[Seq[Boolean]] = params("foo").as[Boolean].apply(request)
    a [RequestErrors] shouldBe thrownBy(Await.result(futureResult))
  }


  "A OptionalIntParams" should "be parsed as a list of integers" in {
    val request: Request = Request(("foo", "5"), ("foo", "255"))
    val futureResult: Future[Seq[Int]] = params("foo").as[Int].apply(request)
    val result: Seq[Int] = Await.result(futureResult)
    result should have length 2
    result should contain(5)
    result should contain(255)
  }

  it should "produce an error if one of the params is not an integer" in {
    val request: Request = Request(("foo", "non-number"), ("foo", "255"))
    val futureResult: Future[Seq[Int]] = params("foo").as[Int].apply(request)
    a [RequestErrors] shouldBe thrownBy(Await.result(futureResult))
  }


  "A OptionalLongParams" should "be parsed as a list of longs" in {
    val request: Request = Request(("foo", "9000000000000000"), ("foo", "7500000000000000"))
    val futureResult: Future[Seq[Long]] = params("foo").as[Long].apply(request)
    val result: Seq[Long] = Await.result(futureResult)
    result should have length 2
    result should contain(9000000000000000L)
    result should contain(7500000000000000L)
  }

  it should "produce an error if one of the params is not a long" in {
    val request: Request = Request(("foo", "false"), ("foo", "7500000000000000"))
    val futureResult: Future[Seq[Long]] = params("foo").as[Long].apply(request)
    a [RequestErrors] shouldBe thrownBy(Await.result(futureResult))
  }

  "A OptionalFloatParams" should "be parsed as a list of floats" in {
    val request: Request = Request(("foo", "5.123"), ("foo", "536.22345"))
    val futureResult: Future[Seq[Float]] = params("foo").as[Float].apply(request)
    val result: Seq[Float] = Await.result(futureResult)
    result should have length 2
    result should contain(5.123f)
    result should contain(536.22345f)
  }

  it should "produce an error if one of the params is not a float" in {
    val request: Request = Request(("foo", "non-number"), ("foo", "true"), ("foo", "5.123"))
    val futureResult: Future[Seq[Float]] = params("foo").as[Float].apply(request)
    a [RequestErrors] shouldBe thrownBy(Await.result(futureResult))
  }


  "A OptionalDoubleParams" should "be parsed as a list of doubles" in {
    val request: Request = Request(("foo", "100.0"), ("foo", "66566.45243"))
    val futureResult: Future[Seq[Double]] = params("foo").as[Double].apply(request)
    val result: Seq[Double] = Await.result(futureResult)
    result should have length 2
    result should contain(100.0)
    result should contain(66566.45243)
  }

  it should "produce an error if one of the params is not a double" in {
    val request: Request = Request(("foo", "45543245.435"), ("foo", "non-number"))
    val futureResult: Future[Seq[Double]] = params("foo").as[Double].apply(request)
    a [RequestErrors] shouldBe thrownBy(Await.result(futureResult))
  }
}
