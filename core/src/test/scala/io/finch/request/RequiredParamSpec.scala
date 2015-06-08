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
import io.finch.HttpRequest
import org.scalatest.{Matchers, FlatSpec}

class RequiredParamSpec extends FlatSpec with Matchers {

  "A RequiredParam" should "be properly parsed if it exists" in {
    val request: HttpRequest = Request(("foo", "5"))
    val futureResult: Future[String] = param("foo")(request)
    Await.result(futureResult) shouldBe "5"
  }

  it should "produce an error if the param is empty" in {
    val request: HttpRequest = Request(("foo", ""))
    val futureResult: Future[String] = param("foo")(request)
    a [NotValid] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "produce an error if the param does not exist" in {
    val request: HttpRequest = Request(("bar", "foo"))
    val futureResult: Future[String] = param("foo")(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "have a matching RequestItem" in {
    val p = "foo"
    param(p).item shouldBe items.ParamItem(p)
  }

  it should "return the correct result when mapped over" in {
    val request: HttpRequest = Request(("foo", "5"))
    val reader: RequestReader[String] = param("foo").map(_ * 3)
    Await.result(reader(request)) shouldBe "555"
  }

  it should "return the correct result when mapped over with arrow syntax" in {
    val request: HttpRequest = Request(("foo", "5"))
    val reader: RequestReader[String] = param("foo") ~> (_ * 3)
    Await.result(reader(request)) shouldBe "555"
  }

  it should "return the correct result when embedFlatMapped over" in {
    val request: HttpRequest = Request(("foo", "5"))
    val reader: RequestReader[String] = param("foo").embedFlatMap { foo =>
      Future.value(foo * 4)
    }
    Await.result(reader(request)) shouldBe "5555"
  }

  it should "return the correct result when embedFlatMapped over with arrow syntax" in {
    val request: HttpRequest = Request(("foo", "5"))
    val reader: RequestReader[String] = param("foo") ~~> { foo =>
      Future.value(foo * 4)
    }
    Await.result(reader(request)) shouldBe "5555"
  }

  "A RequiredBooleanParam" should "be parsed as a boolean" in {
    val request: HttpRequest = Request(("foo", "true"))
    val futureResult: Future[Boolean] = param("foo").as[Boolean].apply(request)
    Await.result(futureResult) shouldBe true
  }

  it should "produce an error if the param is not a boolean" in {
    val request: HttpRequest = Request(("foo", "5"))
    val futureResult: Future[Boolean] = param("foo").as[Boolean].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }


  "A RequiredIntParam" should "be parsed as an integer" in {
    val request: HttpRequest = Request(("foo", "5"))
    val futureResult: Future[Int] = param("foo").as[Int].apply(request)
    Await.result(futureResult) shouldBe 5
  }

  it should "produce an error if the param is not an integer" in {
    val request: HttpRequest = Request(("foo", "non-number"))
    val futureResult: Future[Int] = param("foo").as[Int].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }


  "A RequiredLongParam" should "be parsed as a long" in {
    val request: HttpRequest = Request(("foo", "9000000000000000"))
    val futureResult: Future[Long] = param("foo").as[Long].apply(request)
    Await.result(futureResult) shouldBe 9000000000000000L
  }

  it should "produce an error if the param is not a long" in {
    val request: HttpRequest = Request(("foo", "non-number"))
    val futureResult: Future[Long] = param("foo").as[Long].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }

  "A RequiredFloatParam" should "be parsed as a float" in {
    val request: HttpRequest = Request(("foo", "5.123"))
    val futureResult: Future[Float] = param("foo").as[Float].apply(request)
    Await.result(futureResult) shouldBe 5.123f
  }

  it should "produce an error if the param is not a float" in {
    val request: HttpRequest = Request(("foo", "non-number"))
    val futureResult: Future[Float] = param("foo").as[Float].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }


  "A RequiredDoubleParam" should "be parsed as a double" in {
    val request: HttpRequest = Request(("foo", "100.0"))
    val futureResult: Future[Double] = param("foo").as[Double].apply(request)
    Await.result(futureResult) shouldBe 100.0
  }

  it should "produce an error if the param is not a double" in {
    val request: HttpRequest = Request(("foo", "non-number"))
    val futureResult: Future[Double] = param("foo").as[Double].apply(request)
    a [NotParsed] shouldBe thrownBy(Await.result(futureResult))
  }
}
