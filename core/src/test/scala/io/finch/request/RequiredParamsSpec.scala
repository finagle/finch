/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 * Ryan Plessner
 */

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import io.finch._
import org.scalatest.{Matchers, FlatSpec}

class RequiredParamsSpec extends FlatSpec with Matchers {

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

  it should "have a toString that produces a string representation of itself" in {
    val param = "foo"
    RequiredParams(param).toString should equal(s"Required parameters '$param'")
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
