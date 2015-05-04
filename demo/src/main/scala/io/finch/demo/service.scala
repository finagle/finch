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
import com.twitter.finagle.Service
import com.twitter.util.Future

import io.finch._


object service {

  import model._
  import reader._

  // A REST service that fetches a user with `userId`.
  case class GetUser(userId: Long) extends Service[AuthRequest, User] {
    def apply(req: AuthRequest): Future[User] = Db.select(userId) flatMap {
      case Some(user) => user.toFuture
      case None => UserNotFound(userId).toFutureException[User]
    }
  }

  // A REST service that fetches all users.
  object GetAllUsers extends Service[AuthRequest, Users] {
    def apply(req: AuthRequest): Future[Users] = Db.all
  }

  // A REST service that inserts a new user with `userId`.
  object PostUser extends Service[AuthRequest, User] {
    def apply(req: AuthRequest): Future[User] = for {
      in <- user(req)
      out <- Db.insert(in.id, in)
    } yield out
  }

  // A REST service that add a ticket to a given user `userId`.
  case class PostUserTicket(userId: Long) extends Service[AuthRequest, Ticket] {
    def apply(req: AuthRequest): Future[Ticket] = for {
      t <- ticket(req)
      u <- GetUser(userId)(req) // fetch exist user
      updatedU = u.copy(tickets = u.tickets :+ t) // modify its tickets
      _ <- Db.insert(userId, updatedU)
    } yield t
  }
}
