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
 * Rodrigo Ribeiro
 * Jens Halm
 */
package io.finch.request

import org.scalatest.{Matchers, FlatSpec}
import com.twitter.util.{Try,Return,Future,Await}
import com.twitter.finagle.httpx.Request
import scala.math._
import io.finch.HttpRequest

class DecodeSpec extends FlatSpec with Matchers {

  private def decode[A](json: String)(implicit d: DecodeRequest[A]): Try[A] = d(json)
  
  implicit val decodeInt = DecodeRequest { s => Try(s.toInt) }
  
  "A DecodeJson" should "be accepted as implicit instance of superclass" in {
    implicit object BigDecimalJson extends DecodeRequest[BigDecimal] {
      def apply(s: String): Try[BigDecimal] = Try(BigDecimal(s))
    }

    decode[ScalaNumber]("12345.25") shouldBe Return(BigDecimal(12345.25))
  }
  
  "A RequestReader for a String" should "allow for type converions based on implicit DecodeRequest" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val reader: RequestReader[Int] = RequiredParam("foo").as[Int]
    val result = reader(request)
    Await.result(result) shouldBe 5
  }
  
  it should "fail if a type converions based on implicit DecodeRequest fails" in {
    val request: HttpRequest = Request.apply(("foo", "foo"))
    val reader: RequestReader[Int] = RequiredParam("foo").as[Int]
    val result = reader(request)
    Await.result(result.liftToTry).isThrow shouldBe true
  }
  
  it should "allow for type converions of optional parameters" in {
    val request: HttpRequest = Request.apply(("foo", "5"))
    val reader: RequestReader[Option[Int]] = OptionalParam("foo").as[Int]
    val result = reader(request)
    Await.result(result) shouldBe Some(5)
  }
  
  it should "fail if a type converions for an optional value fails" in {
    val request: HttpRequest = Request.apply(("foo", "foo"))
    val reader: RequestReader[Option[Int]] = OptionalParam("foo").as[Int]
    val result = reader(request)
    Await.result(result.liftToTry).isThrow shouldBe true
  }
  
  it should "skip type conversion and succeed if the optional value is missing" in {
    val request: HttpRequest = Request.apply(("bar", "foo"))
    val reader: RequestReader[Option[Int]] = OptionalParam("foo").as[Int]
    val result = reader(request)
    Await.result(result) shouldBe None
  }
  
  it should "allow for type converions of a parameter list" in {
    val request: HttpRequest = Request.apply(("foo", "5,6,7"))
    val reader: RequestReader[Seq[Int]] = OptionalParams("foo").as[Int]
    val result = reader(request)
    Await.result(result) shouldBe Seq(5,6,7)
  }
  
  it should "fail if a type converion for an element in a parameter list fails" in {
    val request: HttpRequest = Request.apply(("foo", "5,foo,7"))
    val reader: RequestReader[Seq[Int]] = OptionalParams("foo").as[Int]
    val result = reader(request)
    Await.result(result.liftToTry).isThrow shouldBe true
  }
  
  it should "skip type conversion and succeed if a parameter list is empty" in {
    val request: HttpRequest = Request.apply(("bar", "foo"))
    val reader: RequestReader[Seq[Int]] = OptionalParams("foo").as[Int]
    val result = reader(request)
    Await.result(result).isEmpty shouldBe true
  }
  
}
