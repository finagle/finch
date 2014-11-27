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
import io.finch._
import org.scalatest.{Matchers, FlatSpec}

class OptionalParamSpec extends FlatSpec with Matchers {

  "An OptionalParam" should "be properly parsed when it exists" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val futureResult: Future[Option[String]] = OptionalParam("foo")(request)
    Await.result(futureResult) should equal(Some("5"))
  }

  it should "produce an error if the param is empty" in {
    val request: HttpRequest = Request.apply()
    val futureResult: Future[Option[String]] = OptionalParam("foo")(request)
    Await.result(futureResult) should equal(None)
  }


  "An OptionalBooleanParam" should "be parsed as an integer" in {
    val request: HttpRequest = Request.apply(("foo", "true"))
    val futureResult: Future[Option[Boolean]] = OptionalBooleanParam("foo")(request)
    Await.result(futureResult) shouldBe Some(true)
  }

  it should "produce an error if the param is not a number" in {
    val request: HttpRequest = Request.apply(("foo", "non-boolean"))
    val futureResult: Future[Option[Boolean]] = OptionalBooleanParam("foo")(request)
    Await.result(futureResult) should equal(None)
  }


  "An OptionalIntParam" should "be parsed as an integer" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val futureResult: Future[Option[Int]] = OptionalIntParam("foo")(request)
    Await.result(futureResult) should equal(Some(5))
  }

  it should "produce an error if the param is not a number" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Option[Int]] = OptionalIntParam("foo")(request)
    Await.result(futureResult) should equal(None)
  }


  "An OptionalLongParam" should "be parsed as a long" in {
    val request: HttpRequest = Request.apply(("foo", "9000000000000000"))
    val futureResult: Future[Option[Long]] = OptionalLongParam("foo")(request)
    Await.result(futureResult) should equal(Some(9000000000000000L))
  }

  it should "produce an error if the param is not a number" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Option[Long]] = OptionalLongParam("foo")(request)
    Await.result(futureResult) should equal(None)
  }

  "An OptionalFloatParam" should "be parsed as a double" in {
    val request: HttpRequest = Request.apply(("foo", "5.123"))
    val futureResult: Future[Option[Float]] = OptionalFloatParam("foo")(request)
    Await.result(futureResult) should equal(Some(5.123f))
  }

  it should "produce an error if the param is not a number" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Option[Float]] = OptionalFloatParam("foo")(request)
    Await.result(futureResult) should equal(None)
  }


  "An OptionalDoubleParam" should "be parsed as a float" in {
    val request: HttpRequest = Request.apply(("foo", "100.0"))
    val futureResult: Future[Option[Double]] = OptionalDoubleParam("foo")(request)
    Await.result(futureResult) should equal(Some(100.0))
  }

  it should "produce an error if the param is not a number" in {
    val request: HttpRequest = Request.apply(("foo", "non-number"))
    val futureResult: Future[Option[Double]] = OptionalDoubleParam("foo")(request)
    Await.result(futureResult) should equal(None)
  }
}
