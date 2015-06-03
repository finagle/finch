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
import io.finch.demo.model.Ticket
import io.finch.route._

object endpoint {

  import model._
  import service._

  val getUser: Endpoint[AuthRequest, User] = Get  / "users" / long /> GetUser
  val postUser: Endpoint[AuthRequest, User] = Post / "users" /> PostUser
  val getUsers: Endpoint[AuthRequest, List[User]] = Get  / "users" /> GetAllUsers
  val postTicket: Endpoint[AuthRequest, Ticket] = Post / "users" / long / "tickets" /> PostUserTicket
}
