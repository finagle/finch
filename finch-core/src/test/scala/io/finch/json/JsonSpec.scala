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
 */

package io.finch.json

import io.finch._
import com.twitter.finagle.httpx.{Request, Method}
import com.twitter.finagle.httpx.path._
import com.twitter.finagle.Service
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}
import scala.math._

class JsonSpec extends FlatSpec with Matchers {
  val mockService = new Service[HttpRequest, Int] {
    def apply(req: HttpRequest) = 42.toFuture
  }

  implicit val intEncodeJson = new EncodeJson[Int] {
    def apply(n: Int): String = n.toString
  }

  private def encode[A](obj: A)(implicit e: EncodeJson[A]): String = e(obj)
  private def decode[A](json: String)(implicit d: DecodeJson[A]): Option[A] = d(json)

  "A EncodeJson" should "be accepted as implicit instance of subclass" in {
    implicit def seqEncodeJson[A](implicit vEnc: EncodeJson[A]) = new EncodeJson[Seq[A]] {
      def apply(seq: Seq[A]): String = {
        seq.map(vEnc(_)).mkString("[", ", ", "]")
      }
    }

    implicit val scalaNumberEncodeJson = new EncodeJson[ScalaNumber] {
      def apply(n: ScalaNumber): String = n.toString
    }

    encode(Seq(BigDecimal(123l), BigDecimal(0l))) shouldBe "[123, 0]"
    encode(Vector(BigDecimal(123l), BigDecimal(0l))) shouldBe "[123, 0]"
    encode(List(BigInt(123l), BigInt(0l))) shouldBe "[123, 0]"
  }

  "A DecodeJson" should "be accepted as implicit instance of superclass" in {
    implicit object BigDecimalJson extends DecodeJson[BigDecimal] {
      def apply(s: String): Option[BigDecimal] = Some(BigDecimal(s))
    }

    decode[ScalaNumber]("12345.25") shouldBe Some(BigDecimal(12345.25))
  }

  "JsonToHttp" should "convert service output to HttpResponse when encoder is provided" in {
    val endpoint: Endpoint[HttpRequest, HttpResponse] = Endpoint {
      case Method.Get -> Root => mockService
    }
    val service = endpoint.toService

    Await.result(service(Request())).getContentString shouldBe "42"
  }

  it should "convert services output to HttpResponse even without type information" in {
    val endpoint = Endpoint {
      case Method.Get -> Root => mockService.asJson
    }
    val service = endpoint.toService

    Await.result(service(Request())).getContentString shouldBe "42"
  }
}
