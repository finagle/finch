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

import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Future}
import io.finch.HttpRequest
import org.scalatest.{Matchers, FlatSpec}

class RequiredParamSpec extends FlatSpec with Matchers {

  "A RequiredParam" should "be properly parsed if it exists" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val futureResult: Future[String] = RequiredParam("foo")(request)
    Await.result(futureResult) should equal("5")
  }

  it should "produce an error if the param is empty" in {
    val request: HttpRequest = Request.apply(("foo", ""))
    val futureResult: Future[String] = RequiredParam("foo")(request)
    a [ValidationFailed] should be thrownBy Await.result(futureResult)
  }

  it should "produce an error if the param does not exist" in {
    val request: HttpRequest = Request.apply(("bar", "foo"))
    val futureResult: Future[String] = RequiredParam("foo")(request)
    a [ParamNotFound] should be thrownBy Await.result(futureResult)
  }

  "A RequiredBooleanParam" should "be parsed as a boolean" in {
    val request: HttpRequest = Request.apply(("foo", "true"))
    val futureResult: Future[Boolean] = RequiredBooleanParam("foo")(request)
    Await.result(futureResult) shouldBe true
  }

  it should "produce an error if the param is not a boolean" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val futureResult: Future[Boolean] = RequiredBooleanParam("foo")(request)
    a [ValidationFailed] should be thrownBy Await.result(futureResult)
  }


  "A RequiredIntParam" should "be parsed as an integer" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val futureResult: Future[Int] = RequiredIntParam("foo")(request)
    Await.result(futureResult) should equal(5)
  }

  it should "produce an error if the param is not an integer" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Int] = RequiredIntParam("foo")(request)
    a [ValidationFailed] should be thrownBy Await.result(futureResult)
  }


  "A RequiredLongParam" should "be parsed as a long" in {
    val request: HttpRequest = Request.apply(("foo", "9000000000000000"))
    val futureResult: Future[Long] = RequiredLongParam("foo")(request)
    Await.result(futureResult) should equal(9000000000000000L)
  }

  it should "produce an error if the param is not a long" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Long] = RequiredLongParam("foo")(request)
    a [ValidationFailed] should be thrownBy Await.result(futureResult)
  }

  "A RequiredFloatParam" should "be parsed as a float" in {
    val request: HttpRequest = Request.apply(("foo", "5.123"))
    val futureResult: Future[Float] = RequiredFloatParam("foo")(request)
    Await.result(futureResult) should equal(5.123f)
  }

  it should "produce an error if the param is not a float" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Float] = RequiredFloatParam("foo")(request)
    a [ValidationFailed] should be thrownBy Await.result(futureResult)
  }


  "A RequiredDoubleParam" should "be parsed as a double" in {
    val request: HttpRequest = Request.apply(("foo", "100.0"))
    val futureResult: Future[Double] = RequiredDoubleParam("foo")(request)
    Await.result(futureResult) should equal(100.0)
  }

  it should "produce an error if the param is not a double" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Double] = RequiredDoubleParam("foo")(request)
    a [ValidationFailed] should be thrownBy Await.result(futureResult)
  }
}