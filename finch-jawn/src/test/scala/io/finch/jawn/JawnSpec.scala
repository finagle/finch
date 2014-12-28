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

package io.finch.jawn

import _root_.jawn.ast.{JObject, JString, JawnFacade}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

class JawnSpec extends FlatSpec with Matchers {
  implicit val facade = JawnFacade
  val str = "{\"name\": \"bob\" }"
  val jsVal = JObject(mutable.Map("name" -> JString("bob")))

  "A DecodeJawn" should "parse valid json into its ast" in {
    toJawnDecode(facade)(str).foreach(v => v.shouldBe(jsVal))
  }

  "An EncodeJawn" should "render a valid JValue as a string" in {
    EncodeJawn(jsVal) == str
  }
}