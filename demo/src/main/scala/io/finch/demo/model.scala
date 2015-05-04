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
import com.twitter.util.Future
import com.twitter.finagle.Service

import io.finch._

object model {

  // A simple base class for data classes.
  trait ToJson { def toJson: Json }

  // A ticket object with two fields: `id` and `label`.
  case class Ticket(id: Long, label: String) extends ToJson {
    override def toJson: Json = this.asJson
  }

  // A user object with three fields: `id`, `name` and a collection of `tickets`.
  case class User(id: Long, name: String, tickets: List[Ticket]) extends ToJson {
    override def toJson: Json = this.asJson
  }

  case class Users(users: List[User]) extends ToJson {
    override def toJson: Json = users.asJson
  }

  // An exception that indicates missing user with `userId`.
  case class UserNotFound(userId: Long) extends Exception(s"User $userId is not found.")

  // A helper service that turns a model object into JSON.
  object TurnModelIntoJson extends Service[ToJson, Json] {
    def apply(model: ToJson): Future[Json] = Future.value(model.toJson)
  }

  // Provides an in implementation of the EncodeJson Typeclass from Argonaut
  private[model] implicit def encodeUserJson: EncodeJson[User] = EncodeJson((u: User) =>
    ("tickets" := u.tickets) ->: ("name" := u.name) ->: ("id" := u.id) ->: jEmptyObject)

  // Provides an in implementation of the EncodeJson Typeclass from Argonaut
  private[model] implicit def encodeTicketJson: EncodeJson[Ticket] = EncodeJson((t: Ticket) =>
    ("label" := t.label) ->: ("id" := t.id) ->: jEmptyObject)
}
