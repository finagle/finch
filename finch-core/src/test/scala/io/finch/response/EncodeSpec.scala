package io.finch.response
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

import org.scalatest.{Matchers, FlatSpec}
import scala.language.implicitConversions
import scala.math._

class EncodeSpec extends FlatSpec with Matchers {

  private def encode[A](obj: A)(implicit e: EncodeJson[A]): String = e(obj)
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

}