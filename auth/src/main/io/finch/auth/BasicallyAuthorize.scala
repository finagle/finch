/*
 * Copyright 2015, by Vladimir Kostyukov and Contributors.
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
 * Contributor(s): -
 */

package io.finch.auth

import io.finch._
import io.finch.response._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Base64StringEncoder

case class BasicallyAuthorize(user: String, password: String) extends SimpleFilter[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {
    val userInfo = s"$user:$password"
    val expected = "Basic " + Base64StringEncoder.encode(userInfo.getBytes)

    req.authorization match {
      case Some(actual) if actual == expected => service(req)
      case _ => Unauthorized().toFuture
    }
  }
}
