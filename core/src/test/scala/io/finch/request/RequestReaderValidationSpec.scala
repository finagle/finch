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
 * Jens Halm
 */

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class RequestReaderValidationSpec extends FlatSpec with Matchers {

  val request = Request(("foo" -> "6"), ("bar" -> "9"))
  val fooReader = RequiredIntParam("foo")
  val barReader = RequiredIntParam("bar")

  
  val beEven = ValidationRule[Int]("be even") { _ % 2 == 0 }
  def beSmallerThan(value: Int) = ValidationRule[Int](s"be smaller than $value") { _ < value }

  "A RequestReader" should "allow valid values" in {
    val evenReader = fooReader.should("be even") { _ % 2 == 0 }
    Await.result(evenReader(request)) shouldBe 6
  }
  
  it should "allow valid values based on negated rules" in {
    val evenReader = barReader.shouldNot("be even") { _ % 2 == 0 }
    Await.result(evenReader(request)) shouldBe 9
  }

  it should "raise a RequestReader error for invalid values" in {
    val oddReader = fooReader.should("be odd") { _ % 2 != 0 }
    a [RequestReaderError] should be thrownBy Await.result(oddReader(request))
  }

  it should "allow valid values in a for-comprehension" in {
    val readFoo: RequestReader[Int] = for {
      foo <- fooReader if foo % 2 == 0
    } yield foo
    Await.result(readFoo(request)) shouldBe 6
  }

  it should "raise a RequestReader error for invalid values in a for-comprehension" in {
    val readFoo: RequestReader[Int] = for {
      foo <- fooReader if foo % 2 != 0
    } yield foo
    a [RequestReaderError] should be thrownBy Await.result(readFoo(request))
  }
  
  
  "A RequestReader with a predefined validation rule" should "allow valid values" in {
    val evenReader = fooReader.should(beEven)
    Await.result(evenReader(request)) shouldBe 6
  }
  
  it should "allow valid values based on negated rules" in {
    val evenReader = barReader.shouldNot(beEven)
    Await.result(evenReader(request)) shouldBe 9
  }

  it should "raise a RequestReader error for invalid values" in {
    val oddReader = fooReader.shouldNot(beEven)
    a [RequestReaderError] should be thrownBy Await.result(oddReader(request))
  }
  
  it should "allow valid values based on two rules combined with and" in {
    val andReader = fooReader.should(beEven and beSmallerThan(7))
    Await.result(andReader(request)) shouldBe 6
  }
  
  it should "raise a RequestReader error if one of two rules combined with and fails" in {
    val andReader = fooReader.should(beEven and beSmallerThan(2))
    a [RequestReaderError] should be thrownBy Await.result(andReader(request))
  }
  
  it should "allow valid values based on two rules combined with or" in {
    val orReader = barReader.shouldNot(beEven or beSmallerThan(2))
    Await.result(orReader(request)) shouldBe 9
  }
  
  it should "raise a RequestReader error if one of two rules combined with or in a negation fails" in {
    val andReader = fooReader.shouldNot(beEven or beSmallerThan(12))
    a [RequestReaderError] should be thrownBy Await.result(andReader(request))
  }
  
  it should "allow to reuse a validation rule with optional readers" in {
    val optReader = OptionalIntParam("foo").should(beEven)
  }
  
  
}
