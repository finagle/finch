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
import com.twitter.util.{Await, Future, Return, Throw}
import org.scalatest.{Matchers, FlatSpec}
import org.scalatest.prop.Checkers

class RequestReaderValidationSpec extends FlatSpec with Matchers with Checkers {

  val request = Request("foo" -> "6", "bar" -> "9")
  val fooReader = param("foo").as[Int]
  val barReader = param("bar").as[Int]

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
    a [RequestError] shouldBe thrownBy(Await.result(oddReader(request)))
  }

  it should "be lift-able into an optional reader that always succeeds" in {
    val oddReader = fooReader.should("be odd") { _ % 2 != 0 }
    Await.result(oddReader.lift(request)) shouldBe None
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
    a [RequestError] shouldBe thrownBy(Await.result(readFoo(request)))
  }

  it should "be convertible to a single-member case class with a matching type" in {
    case class Bar(i: Int)
    val barReader = fooReader.as[Bar]

    Await.result(barReader(request)) shouldBe Bar(6)
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
    a [RequestError] shouldBe thrownBy(Await.result(oddReader(request)))
  }
  
  it should "allow valid values based on two rules combined with and" in {
    val andReader = fooReader.should(beEven and beSmallerThan(7))
    Await.result(andReader(request)) shouldBe 6
  }
  
  it should "raise a RequestReader error if one of two rules combined with and fails" in {
    val andReader = fooReader.should(beEven and beSmallerThan(2))
    a [RequestError] shouldBe thrownBy(Await.result(andReader(request)))
  }
  
  it should "allow valid values based on two rules combined with or" in {
    val orReader = barReader.shouldNot(beEven or beSmallerThan(2))
    Await.result(orReader(request)) shouldBe 9
  }
  
  it should "raise a RequestReader error if one of two rules combined with or in a negation fails" in {
    val andReader = fooReader.shouldNot(beEven or beSmallerThan(12))
    a [RequestError] shouldBe thrownBy(Await.result(andReader(request)))
  }
  
  it should "allow to reuse a validation rule with optional readers" in {
    val optReader = paramOption("foo").as[Int].should(beEven)
    Await.result(optReader(request)) shouldBe Some(6)
  }
  
  it should "raise a RequestReader error if a rule for a non-empty optional value fails" in {
    val optReader = paramOption("bar").as[Int].should(beEven)
    a [RequestError] shouldBe thrownBy(Await.result(optReader(request)))
  }
  
  it should "skip validation when applied to an empty optional value" in {
    val optReader = paramOption("baz").as[Int].should(beEven)
    Await.result(optReader(request)) shouldBe None
  }

  it should "work with predefined rules" in {
    val intReader = param("foo").as[Int] should beGreaterThan(100)
    val floatReader = param("bar").as[Float].should(beGreaterThan(100.0f))
    val stringReader = param("baz").should(beLongerThan(10))
    val optLongReader = paramOption("foo").as[Int] should beGreaterThan(100)

    val req = Request("foo" -> "20", "bar" -> "20.0", "baz" -> "baz")

    a [RequestError] shouldBe thrownBy(Await.result(intReader(req)))
    a [RequestError] shouldBe thrownBy(Await.result(floatReader(req)))
    a [RequestError] shouldBe thrownBy(Await.result(stringReader(req)))
    a [RequestError] shouldBe thrownBy(Await.result(optLongReader(req)))
  }

  it should "allow to use inline rules with optional params" in {
    val optInt = paramOption("foo").as[Int].should("be greater than 50") { i: Int => i > 50 }
    val optString = paramOption("bar").should("be longer than 5 chars") { s: String => s.length > 5 }

    a [RequestError] shouldBe thrownBy(Await.result(optInt(request)))
    a [RequestError] shouldBe thrownBy(Await.result(optString(request)))
  }

  "A composite RequestReader" should "be convertible to an appropriately typed case class" in {
    case class Qux(i: Int, j: Int)
    val quxReader = (fooReader :: barReader).as[Qux]

    Await.result(quxReader(request)) shouldBe Qux(6, 9)
  }

  it should "be convertible to a tuple" in {
    val tupleReader = (fooReader :: barReader).asTuple

    Await.result(tupleReader(request)).shouldBe((6, 9))
  }

  it should "correctly fail with a single error" in {
    val firstBadReader = (fooReader.shouldNot(beEven) :: barReader.shouldNot(beEven)).asTuple
    val secondBadReader = (fooReader.should(beEven) :: barReader.should(beEven)).asTuple

    Await.result(firstBadReader(request).liftToTry) should matchPattern {
      case Throw(NotValid(_, _)) =>
    }

    Await.result(secondBadReader(request).liftToTry) should matchPattern {
      case Throw(NotValid(_, _)) =>
    }
  }

  it should "correctly accumulate errors" in {
    val tupleReader = (fooReader.shouldNot(beEven) :: barReader.should(beEven)).asTuple

    Await.result(tupleReader(request).liftToTry) should matchPattern {
      case Throw(RequestErrors(Seq(NotValid(_, _), NotValid(_, _)))) =>
    }
  }

  it should "be able to have a function with appropriate arity and types applied to it" in {
    val add: (Int, Int) => Int = _ + _
    val sumReader = (fooReader :: barReader) ~> add

    check { (foo: Int, bar: Int) =>
      val req = Request("foo" -> foo.toString, "bar" -> bar.toString)

      Await.result(sumReader(req)) === foo + bar
    }
  }

  it should "be able to have an appropriately-typed Future-returning function applied to it" in {
    val div: (Int, Int) => Future[Int] = (x, y) => Future(x / y)
    val divReader = (fooReader :: barReader) ~~> div

    check { (foo: Int, bar: Int) =>
      val req = Request("foo" -> foo.toString, "bar" -> bar.toString)

      Await.result {
        for {
          result <- div(foo, bar).liftToTry
          readResult <- divReader(req).liftToTry
        } yield (readResult, result) match {
          case (Return(r1), Return(r2)) => r1 == r2
          case (Throw(e1), Throw(e2)) => e1.getMessage == e2.getMessage
          case _ => false
        }
      }
    }
  }
}
