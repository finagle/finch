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

package io.finch.auth

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.{Await, Base64StringEncoder, Future}
import io.finch.response.Ok
import io.finch.{HttpRequest, HttpResponse, _}
import org.scalatest.{Matchers, FlatSpec}

class BasicallyAuthorizeSpec extends FlatSpec with Matchers {

  "A BasicallyAuthorize" should "produce an Unauthorized response if given the wrong credentials" in {
    val auth = BasicallyAuthorize("admin", "password")
    val request = Request()
    request.headers().set("Authorization", encode("wrong", "login"))
    val futureResult = auth(request, okService())
    val result = Await.result(futureResult)

    result.status shouldBe Status.Unauthorized
  }

  it should "pass the user through to the given service if the correct credentials" in {
    val auth = BasicallyAuthorize("admin", "password")
    val request = Request()
    request.headers().set("Authorization", encode("admin", "password"))
    val futureResult = auth(request, okService())
    val result = Await.result(futureResult)

    result.status shouldBe Status.Ok
  }

  private def encode(user: String, password: String) = "Basic " + Base64StringEncoder.encode(s"$user:$password".getBytes)

  private def okService() = new Service[HttpRequest, HttpResponse] {
    override def apply(request: HttpRequest): Future[HttpResponse] = Ok().toFuture
  }
}