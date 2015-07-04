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

package io.finch.response

import com.twitter.finagle.httpx.{Request, Status}
import com.twitter.finagle.httpx.path.Root
import com.twitter.util.Await
import org.scalatest.{Matchers, FlatSpec}

class RedirectSpec extends FlatSpec with Matchers {

  "A Redirect" should "create a service from a string url that generates a redirect" in {
    val redirect = Redirect("/some/route")
    val request = Request()
    val futureResponse = redirect(request)
    val response = Await.result(futureResponse)

    response.status shouldBe Status.SeeOther
    response.headerMap shouldBe Map("Location" -> "/some/route")
  }

  it should "create a service from a path that generates a redirect" in {
    val redirect = Redirect(Root / "some" / "route")
    val request = Request()
    val futureResponse = redirect(request)
    val response = Await.result(futureResponse)

    response.status shouldBe Status.SeeOther
    response.headerMap shouldBe Map("Location" -> "/some/route")
  }
}
