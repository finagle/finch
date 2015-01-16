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
 * Ben Edwards
 */

package io.finch


import com.twitter.finagle.Service
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import io.finch.response.Ok
import org.scalatest.{Matchers, FlatSpec}

class ServiceOpsSpec extends FlatSpec with Matchers {
  val foo = Service.mk { (_: HttpRequest) => Future.value("foo") }
  val bar = Service.mk {
    (req: String) => {
      Future.value(Ok(req ++ "bar"))
    }
  }
  val combined = foo ! bar

  "ServiceOps" should "allow for chaining services" in {
    val req = Request("/")
    val content = combined(req) map { r => r.getContentString }
    Await.result(content) shouldBe "foobar"
  }
}

