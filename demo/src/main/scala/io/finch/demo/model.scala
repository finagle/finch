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

import argonaut._, Argonaut._

object model {

  // A ticket object with two fields: `id` and `label`.
  case class Ticket(id: Long, label: String)

  object Ticket {
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def ticketEncoding: EncodeJson[Ticket] = jencode2L(
      (t: Ticket) => (t.id, t.label)
    )("label", "id")
  }

  // A user object with three fields: `id`, `name` and a collection of `tickets`.
  case class User(id: Long, name: String, tickets: List[Ticket])

  object User {
    // Provides an implementation of the EncodeJson type class from Argonaut
    implicit def userEncoding: EncodeJson[User] = jencode3L(
      (u: User) => (u.id, u.name, u.tickets)
    )("id", "name", "tickets")
  }

  // An exception that indicates missing user with `userId`.
  case class UserNotFound(userId: Long) extends Exception(s"User $userId is not found.")
}
