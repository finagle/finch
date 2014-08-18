package io.finch.request

import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Future}
import io.finch._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class RequiredParamsSpec extends FlatSpec {

  "A RequiredParams" should "parse all of the url params with the same key" in {
    val request: HttpRequest = Request.apply(("foo", "5"), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[List[String]] = RequiredParams("foo")(request)
    val result: List[String] = Await.result(futureResult)
    result should have length 2
    result should contain("5")
    result should contain("25")
  }

  it should "return only params that are not the empty string" in {
    val request: HttpRequest = Request.apply(("foo", ""), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[List[String]] = RequiredParams("foo")(request)
    val result: List[String] = Await.result(futureResult)
    result should have length 1
    result should contain("25")
  }

  it should "throw a ParamNotFound Exception if the parameter does not exist at all" in {
    val request: HttpRequest = Request.apply(("foo", "5"), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[List[String]] = RequiredParams("baz")(request)
    intercept[ParamNotFound] {
      Await.result(futureResult)
    }
  }

  it should "throw a Validation Exception if all of the parameter values are empty" in {
    val request: HttpRequest = Request.apply(("foo", ""), ("bar", "6"), ("foo", ""))
    val futureResult: Future[List[String]] = RequiredParams("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }


  "A RequiredBooleanParams" should "be parsed as a list of booleans" in {
    val request: HttpRequest = Request.apply(("foo", "true"), ("foo", "false"))
    val futureResult: Future[List[Boolean]] = RequiredBooleanParams("foo")(request)
    val result: List[Boolean] = Await.result(futureResult)
    result should have length 2
    result should contain(true)
    result should contain(false)
  }

  it should "produce an error if one of the params is not a boolean" in {
    val request: HttpRequest = Request.apply(("foo", "true"), ("foo", "5"))
    val futureResult: Future[List[Boolean]] = RequiredBooleanParams("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }


  "A RequiredIntParams" should "be parsed as a list of integers" in {
    val request: HttpRequest = Request.apply(("foo", "5"), ("foo", "255"))
    val futureResult: Future[List[Int]] = RequiredIntParams("foo")(request)
    val result: List[Int] = Await.result(futureResult)
    result should have length 2
    result should contain(5)
    result should contain(255)
  }

  it should "produce an error if one of the params is not an integer" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"), ("foo", "255"))
    val futureResult: Future[Int] = RequiredIntParam("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }


  "A RequiredLongParams" should "be parsed as a list of longs" in {
    val request: HttpRequest = Request.apply(("foo", "9000000000000000"), ("foo", "7500000000000000"))
    val futureResult: Future[List[Long]] = RequiredLongParams("foo")(request)
    val result: List[Long] = Await.result(futureResult)
    result should have length 2
    result should contain(9000000000000000L)
    result should contain(7500000000000000L)
  }

  it should "produce an error if one of the params is not a long" in {
    val request: HttpRequest = Request.apply(("foo", "false"), ("foo", "7500000000000000"))
    val futureResult: Future[List[Long]] = RequiredLongParams("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }


  "A RequiredFloatParams" should "be parsed as a list of floats" in {
    val request: HttpRequest = Request.apply(("foo", "5.123"), ("foo", "536.22345"))
    val futureResult: Future[List[Float]] = RequiredFloatParams("foo")(request)
    val result: List[Float] = Await.result(futureResult)
    result should have length 2
    result should contain(5.123f)
    result should contain(536.22345f)
  }

  it should "produce an error if one of the params is not a float" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"), ("foo", "true"))
    val futureResult: Future[List[Float]] = RequiredFloatParams("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }


  "A RequiredDoubleParams" should "be parsed as a list of doubles" in {
    val request: HttpRequest = Request.apply(("foo", "100.0"), ("foo", "66566.45243"))
    val futureResult: Future[List[Double]] = RequiredDoubleParams("foo")(request)
    val result: List[Double] = Await.result(futureResult)
    result should have length 2
    result should contain(100.0)
    result should contain(66566.45243)
  }

  it should "produce an error if one of the params is not a double" in {
    val request: HttpRequest = Request.apply(("foo", "45543245.435"), ("foo", "non-number"))
    val futureResult: Future[List[Double]] = RequiredDoubleParams("foo")(request)
    intercept[ValidationFailed] {
      Await.result(futureResult)
    }
  }

}