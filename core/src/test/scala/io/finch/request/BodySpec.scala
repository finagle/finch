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
 * Ben Whitehead
 */

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.io.Buf.ByteArray
import com.twitter.util.{Await, Future, Try}
import io.finch.HttpRequest
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.scalatest.{FlatSpec, Matchers}
import items._

class BodySpec extends FlatSpec with Matchers {
  val foo = "foo"
  val fooBytes = foo.getBytes("UTF-8")

  "A RequiredArrayBody" should "be properly read if it exists" in {
    val request: HttpRequest = requestWithBody(fooBytes)
    val futureResult: Future[Array[Byte]] = RequiredArrayBody(request)
    Await.result(futureResult) should equal(fooBytes)
  }

  it should "produce an error if the body is empty" in {
    val request: HttpRequest = requestWithBody(Array[Byte]())
    val futureResult: Future[Array[Byte]] = RequiredArrayBody(request)
    a [NotFound] should be thrownBy Await.result(futureResult)
  }

  it should "have a corresponding RequestItem" in {
    RequiredArrayBody.item should equal(BodyItem)
  }

  "An OptionalArrayBody" should "be properly read if it exists" in {
    val request: HttpRequest = requestWithBody(fooBytes)
    val futureResult: Future[Option[Array[Byte]]] = OptionalArrayBody(request)
    Await.result(futureResult).get should equal(fooBytes)
  }

  it should "produce an error if the body is empty" in {
    val request: HttpRequest = requestWithBody(Array[Byte]())
    val futureResult: Future[Option[Array[Byte]]] = OptionalArrayBody(request)
    Await.result(futureResult) should equal(None)
  }

  it should "have a corresponding RequestItem" in {
    OptionalArrayBody.item should equal(BodyItem)
  }

  "A RequiredStringBody" should "be properly read if it exists" in {
    val request: HttpRequest = requestWithBody(foo)
    val futureResult: Future[String] = RequiredStringBody(request)
    Await.result(futureResult) should equal(foo)
  }

  it should "produce an error if the body is empty" in {
    val request: HttpRequest = requestWithBody("")
    val futureResult: Future[String] = RequiredStringBody(request)
    a [NotFound] should be thrownBy Await.result(futureResult)
  }

  "An OptionalStringBody" should "be properly read if it exists" in {
    val request: HttpRequest = requestWithBody(foo)
    val futureResult: Future[Option[String]] = OptionalStringBody(request)
    Await.result(futureResult) should equal(Some(foo))
  }

  it should "produce an error if the body is empty" in {
    val request: HttpRequest = requestWithBody("")
    val futureResult: Future[Option[String]] = OptionalStringBody(request)
    Await.result(futureResult) should equal(None)
  }

  "RequiredArrayBody Reader" should "work without parentheses at call site" in {
    val reader = for {
      body <- RequiredArrayBody
    } yield body

    val request: HttpRequest = requestWithBody(fooBytes)
    Await.result(reader(request)) should equal(fooBytes)
  }

  "RequiredBody and OptionalBody" should "work with no request type available" in {
    implicit val decodeInt = new DecodeRequest[Int] {
       def apply(req: String): Try[Int] = Try(req.toInt)
    }
    val req = requestWithBody("123")
    val ri: RequestReader[Int] = RequiredBody[Int]
    val i: Future[Int] = RequiredBody[Int](req)
    val oi: RequestReader[Option[Int]] = OptionalBody[Int]
    val o = OptionalBody[Int](req)

    Await.result(ri(req)) shouldBe 123
    Await.result(i) shouldBe 123
    Await.result(oi(req)) shouldBe Some(123)
    Await.result(o) shouldBe Some(123)
  }

  it should "work with custom request and its implicit view to HttpRequest" in {
    implicit val decodeDouble = new DecodeRequest[Double] { // custom encoder
      def apply(req: String): Try[Double] = Try(req.toDouble)
    }
    case class CReq(http: HttpRequest) // custom request
    implicit val cReqEv = (req: CReq) => req.http // implicit view

    val req = CReq(requestWithBody("42.0"))
    val rd: RequestReader[Double] = RequiredBody[Double]
    val d = RequiredBody[Double](req)
    val od: RequestReader[Option[Double]] = OptionalBody[Double]
    val o: Future[Option[Double]] = OptionalBody[Double](req)

    Await.result(rd(req)) shouldBe 42.0
    Await.result(d) shouldBe 42.0
    Await.result(od(req)) shouldBe Some(42.0)
    Await.result(o) shouldBe Some(42.0)
  }
  
  it should "fail if the decoding of the body fails" in {
    implicit val decodeInt = new DecodeRequest[Int] {
       def apply(req: String): Try[Int] = Try(req.toInt)
    }
    val req = requestWithBody("foo")
    val ri: RequestReader[Int] = RequiredBody[Int]
    val i: Future[Int] = RequiredBody[Int](req)
    val oi: RequestReader[Option[Int]] = OptionalBody[Int]
    val o: Future[Option[Int]] = OptionalBody[Int](req)

    a [NotParsed] should be thrownBy Await.result(ri(req))
    a [NotParsed] should be thrownBy Await.result(i)
    a [NotParsed] should be thrownBy Await.result(oi(req))
    a [NotParsed] should be thrownBy Await.result(o)
  }

  private[this] def requestWithBody(body: String): HttpRequest = {
    requestWithBody(body.getBytes("UTF-8"))
  }

  private[this] def requestWithBody(body: Array[Byte]): HttpRequest = {
    val r = Request()
    r.content = ByteArray.Owned(body)
    r.headerMap.update(HttpHeaders.Names.CONTENT_LENGTH, body.length.toString)
    r
  }
}
