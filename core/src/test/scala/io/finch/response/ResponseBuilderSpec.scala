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

package io.finch.response

import com.twitter.finagle.httpx.{Status, Cookie}
import org.scalatest.{Matchers, FlatSpec}

class ResponseBuilderSpec extends FlatSpec with Matchers {

  "A ResponseBuilder" should "have the status code that it is set with" in {
    val str = "Some Content!"
    val rep = ResponseBuilder(Status.Ok)(str)
    rep.status shouldBe Status.Ok
  }

  it should "set plain text as its content string" in {
    val str = "Some Content!"
    val rep = ResponseBuilder(Status.Ok)(str)
    rep.getContentString() shouldBe str
    rep.mediaType shouldBe Some("text/plain")
  }

  it should "only include that headers that are set on it" in {
    val rep = Ok.withHeaders("Location" -> "/somewhere")()
    rep.headerMap shouldBe Map("Location" -> "/somewhere")
  }

  it should "build empty responses with status" in {
    val rep = SeeOther()
    rep.getContentString() shouldBe ""
    rep.status shouldBe Status.SeeOther
  }

  it should "include cookies that are set on it" in {
    val cookie = new Cookie("session", "random-string")
    val rep = Ok.withCookies(cookie)
    val response = rep()

    rep.cookies shouldBe Seq(cookie)
    response.cookies.get("session") shouldBe Some(cookie)
  }

  it should "override the contentType" in {
    val ok = Ok.withContentType(Some("application/json"))
    // text/plain is provided but application/json should be used
    val rep = ok("foo")

    rep.contentType shouldBe Some("application/json;charset=utf-8")
  }
}
