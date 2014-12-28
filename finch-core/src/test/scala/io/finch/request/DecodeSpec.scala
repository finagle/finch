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
package io.finch.request

import org.scalatest.{Matchers, FlatSpec}
import scala.math._

class DecodeSpec extends FlatSpec with Matchers {

  private def decode[A](json: String)(implicit d: DecodeJson[A]): Option[A] = d(json)
  "A DecodeJson" should "be accepted as implicit instance of superclass" in {
    implicit object BigDecimalJson extends DecodeJson[BigDecimal] {
      def apply(s: String): Option[BigDecimal] = Some(BigDecimal(s))
    }

    decode[ScalaNumber]("12345.25") shouldBe Some(BigDecimal(12345.25))
  }
}
