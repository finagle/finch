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

import com.twitter.finagle.httpx.{Request, Cookie}
import com.twitter.util.{Await, Future}
import io.finch.HttpRequest
import org.scalatest.{Matchers, FlatSpec}

class CookieSpec extends FlatSpec with Matchers {

  val name = "session"
  val c = new Cookie(name, "some-random-value")

  "A ResponseBuilder" should "read a required cookie" in {
    val request: HttpRequest = Request()
    request.addCookie(c)
    val futureResult: Future[Cookie] = cookie(name)(request)

    Await.result(futureResult) shouldBe c
  }

  it should "throw an exception if the require cookie does not exist" in {
    val request: HttpRequest = Request()
    request.addCookie(c)
    val futureResult: Future[Cookie] = cookie("another-cookie")(request)

    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "read an optional cookie if it exists" in {
    val request: HttpRequest = Request()
    request.addCookie(c)
    val futureResult: Future[Option[Cookie]] = cookieOption(name)(request)

    Await.result(futureResult) shouldBe Some(c)
  }

  it should "read None if the cookie name does not exist" in {
    val request: HttpRequest = Request()
    val futureResult: Future[Option[Cookie]] = cookieOption(name)(request)

    Await.result(futureResult) shouldBe None
  }

  it should "have a matching RequestItem" in {
    cookie(name).item shouldBe items.CookieItem(name)
    cookieOption(name).item shouldBe items.CookieItem(name)
  }
}
