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

import com.twitter.finagle.{SimpleFilter, Service}
import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future}
import org.scalatest.{Matchers, FlatSpec}

class FilterOpsSpec extends FlatSpec with Matchers {

  private[finch] class PrefixFilter(val prefix: String) extends SimpleFilter[Request, String] {
    def apply(req: Request, service: Service[Request, String]): Future[String] = {
      service(req) map { rep => prefix ++ rep }
    }
  }

  val bar = Service.mk { (_: Request) => Future.value("bar") }
  val req = Request("/")

  "FilterOps" should "allow for chaining a filter to a service" in {
    val foo = new PrefixFilter("foo")
    val combined = foo ! bar

    Await.result(combined(req)) shouldBe "foobar"
  }

  it should "allow for chaining filters to filters" in {
    val fo = new PrefixFilter("fo")
    val oo = new PrefixFilter("oo")
    val combined = fo ! oo ! bar

    Await.result(combined(req)) shouldBe "fooobar"
  }
}

