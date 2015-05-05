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

package io.finch.demo

import argonaut.{EncodeJson, Json}
import com.twitter.finagle.Service
import com.twitter.util.Future
import io.finch.route._

object endpoint {

  import service._

  implicit class JsonEndpoint[Req, Rep](endpoint: Endpoint[Req, Rep]) {
    def toJson(implicit encoding: EncodeJson[Rep]): Endpoint[Req, Json] = endpoint.map { service =>
      new Service[Req, Json] {
        def apply(req: Req): Future[Json] = service(req).map(encoding(_))
      }
    }
  }

  // User endpoint.
  val users: Endpoint[AuthRequest, Json] =
    (Get / "users" / long /> GetUser).toJson |
    (Post / "users" /> PostUser).toJson |
    (Get / "users" /> GetAllUsers).toJson

  // Ticket endpoint.
  val tickets: Endpoint[AuthRequest, Json] =
    (Post / "users" / long / "tickets" /> PostUserTicket).toJson
}
