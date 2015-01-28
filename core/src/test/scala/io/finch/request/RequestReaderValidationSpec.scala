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

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class RequestReaderValidationSpec extends FlatSpec with Matchers {

  val request = Request(("foo", "6"))
  val reader = RequiredIntParam("foo")

  "A RequestReader" should "allow valid values" in {
    val evenReader = reader.should("be even") { _ % 2 == 0 }
    Await.result(evenReader(request)) shouldBe 6
  }

  it should "raise a RequestReader error for invalid values" in {
    val oddReader = reader.should("be odd") { _ % 2 != 0 }
    a [RequestReaderError] should be thrownBy Await.result(oddReader(request))
  }

  it should "allow valid values in a for-comprehension" in {
    val readFoo: RequestReader[Int] = for {
      foo <- reader if foo % 2 == 0
    } yield foo
    Await.result(readFoo(request)) shouldBe 6
  }

  it should "raise a RequestReader error for invalid values in a for-comprehension" in {
    val readFoo: RequestReader[Int] = for {
      foo <- reader if foo % 2 != 0
    } yield foo
    a [RequestReaderError] should be thrownBy Await.result(readFoo(request))
  }
}