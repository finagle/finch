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
 */

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class ValidationRuleSpec extends FlatSpec with Matchers {

  "A ValidationRule" should "do not throw an error if it passes validation" in {
    val request = Request(("user", "bob"))
    val user = for {
      u <- RequiredParam("user")
      _ <- ValidationRule("user", "user should not be empty") { !u.isEmpty }
    } yield u
    Await.result(user(request)) should be ("bob")
  }

  "A ValidationRule" should "throw an error if validation fails" in {
    val request = Request(("user", "not-bob"))
    val user = for {
      u <- RequiredParam("user")
      _ <- ValidationRule("user", "user should not be empty") { u == "bob" }
    } yield u
    a [ValidationFailed] should be thrownBy Await.result(user(request))
  }
}
