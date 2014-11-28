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

package io.finch.json

import io.finch.finchjawn.{EncodeJawn, DecodeJawn}
import jawn.ast.{JawnFacade, JString, JObject}

import scala.collection.mutable
import org.scalatest.{Matchers, FlatSpec}

class JawnSpec extends FlatSpec with Matchers {

  val str = "{\"name\": \"bob\" }"
  val jsVal = JObject(mutable.Map("name" -> JString("bob")))

  "A DecodeJawn" should "parse valid json into its ast" in {
    DecodeJawn(JawnFacade)(str).foreach(_ shouldBe jsVal)
  }

  "An EncodeJawn" should "render a valid JValue as a string" in {
    EncodeJawn(jsVal) == str
  }
}