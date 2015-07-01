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

import io.finch.demo.model.{Ticket, User}
import io.finch.demo.service._
import io.finch.route._

object endpoint {
  val getUser: Endpoint[AuthRequest, User] = get("users" / long) /> GetUser
  val postUser: Endpoint[AuthRequest, User] = post("users") /> PostUser
  val getUsers: Endpoint[AuthRequest, List[User]] = get("users") /> GetAllUsers
  val postTicket: Endpoint[AuthRequest, Ticket] = post("users" / long / "tickets") /> PostUserTicket
}
