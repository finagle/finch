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

  val cookieName = "session"
  val cookie = new Cookie(cookieName, "some-random-value")

  "A ResponseBuilder" should "read a required cookie" in {
    val request: HttpRequest = Request()
    request.addCookie(cookie)
    val futureResult: Future[Cookie] = RequiredCookie(cookieName)(request)

    Await.result(futureResult) should equal(cookie)
  }

  it should "throw an exception if the require cookie does not exist" in {
    val request: HttpRequest = Request()
    request.addCookie(cookie)
    val futureResult: Future[Cookie] = RequiredCookie("another-cookie")(request)

    a [NotPresent] should be thrownBy Await.result(futureResult)
  }

  it should "read an optional cookie if it exists" in {
    val request: HttpRequest = Request()
    request.addCookie(cookie)
    val futureResult: Future[Option[Cookie]] = OptionalCookie(cookieName)(request)

    Await.result(futureResult) should equal(Some(cookie))
  }

  it should "read None if the cookie name does not exist" in {
    val request: HttpRequest = Request()
    val futureResult: Future[Option[Cookie]] = OptionalCookie(cookieName)(request)

    Await.result(futureResult) should equal(None)
  }

  it should "have a matching RequestItem" in {
    RequiredCookie(cookieName).item should equal(items.CookieItem(cookieName))
    OptionalCookie(cookieName).item should equal(items.CookieItem(cookieName))
  }
}
