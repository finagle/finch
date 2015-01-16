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
 */

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import io.finch._
import org.scalatest.{Matchers, FlatSpec}

class OptionalParamsSpec extends FlatSpec with Matchers {

  "A OptionalParams" should "parse all of the url params with the same key" in {
    val request: HttpRequest = Request.apply(("foo", "5"), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[List[String]] = OptionalParams("foo")(request)
    val result: List[String] = Await.result(futureResult)
    result should have length 2
    result should contain("5")
    result should contain("25")
  }

  it should "return only params that are not the empty string" in {
    val request: HttpRequest = Request.apply(("foo", ""), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[List[String]] = OptionalParams("foo")(request)
    val result: List[String] = Await.result(futureResult)
    result should have length 1
    result should contain("25")
  }

  it should "return Nil if the parameter does not exist at all" in {
    val request: HttpRequest = Request.apply(("foo", "5"), ("bar", "6"), ("foo", "25"))
    val futureResult: Future[List[String]] = OptionalParams("baz")(request)
    Await.result(futureResult) should be (Nil)
  }

  it should "return Nil if all of the values for that parameter ar the emptry string" in {
    val request: HttpRequest = Request.apply(("foo", ""), ("bar", "6"), ("foo", ""))
    val futureResult: Future[List[String]] = OptionalParams("")(request)
    Await.result(futureResult) should be (Nil)
  }


  "A OptionalBooleanParams" should "be parsed as a list of booleans" in {
    val request: HttpRequest = Request.apply(("foo", "true"), ("foo", "false"))
    val futureResult: Future[List[Boolean]] = OptionalBooleanParams("foo")(request)
    val result: List[Boolean] = Await.result(futureResult)
    result should have length 2
    result should contain(true)
    result should contain(false)
  }

  it should "only include valid booleans" in {
    val request: HttpRequest = Request.apply(("foo", "true"), ("foo", "5"))
    val futureResult: Future[List[Boolean]] = OptionalBooleanParams("foo")(request)
    val result: List[Boolean] = Await.result(futureResult)
    result should have length 1
    result should contain(true)
  }


  "A OptionalIntParams" should "be parsed as a list of integers" in {
    val request: HttpRequest = Request.apply(("foo", "5"), ("foo", "255"))
    val futureResult: Future[List[Int]] = OptionalIntParams("foo")(request)
    val result: List[Int] = Await.result(futureResult)
    result should have length 2
    result should contain(5)
    result should contain(255)
  }

  it should "only include valid integers" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"), ("foo", "255"))
    val futureResult: Future[List[Int]] = OptionalIntParams("foo")(request)
    val result: List[Int] = Await.result(futureResult)
    result should have length 1
    result should contain(255)
  }


  "A OptionalLongParams" should "be parsed as a list of longs" in {
    val request: HttpRequest = Request.apply(("foo", "9000000000000000"), ("foo", "7500000000000000"))
    val futureResult: Future[List[Long]] = OptionalLongParams("foo")(request)
    val result: List[Long] = Await.result(futureResult)
    result should have length 2
    result should contain(9000000000000000L)
    result should contain(7500000000000000L)
  }

  it should "only include valid longs" in {
    val request: HttpRequest = Request.apply(("foo", "false"), ("foo", "7500000000000000"))
    val futureResult: Future[List[Long]] = OptionalLongParams("foo")(request)
    val result: List[Long] = Await.result(futureResult)
    result should have length 1
    result should contain(7500000000000000L)
  }

  "A OptionalFloatParams" should "be parsed as a list of floats" in {
    val request: HttpRequest = Request.apply(("foo", "5.123"), ("foo", "536.22345"))
    val futureResult: Future[List[Float]] = OptionalFloatParams("foo")(request)
    val result: List[Float] = Await.result(futureResult)
    result should have length 2
    result should contain(5.123f)
    result should contain(536.22345f)
  }

  it should "only include valid floats" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"), ("foo", "true"), ("foo", "5.123"))
    val futureResult: Future[List[Float]] = OptionalFloatParams("foo")(request)
    val result: List[Float] = Await.result(futureResult)
    result should have length 1
    result should contain(5.123f)
  }


  "A OptionalDoubleParams" should "be parsed as a list of doubles" in {
    val request: HttpRequest = Request.apply(("foo", "100.0"), ("foo", "66566.45243"))
    val futureResult: Future[List[Double]] = OptionalDoubleParams("foo")(request)
    val result: List[Double] = Await.result(futureResult)
    result should have length 2
    result should contain(100.0)
    result should contain(66566.45243)
  }

  it should "only include valid doubles" in {
    val request: HttpRequest = Request.apply(("foo", "45543245.435"), ("foo", "non-number"))
    val futureResult: Future[List[Double]] = OptionalDoubleParams("foo")(request)
    val result: List[Double] = Await.result(futureResult)
    result should have length 1
    result should contain(45543245.435)
  }
}
