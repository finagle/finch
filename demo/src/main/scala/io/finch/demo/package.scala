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

package io.finch

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import scala.collection.JavaConverters._

import com.twitter.util.Future

/**
 * The ''demo'' project shows the usage of Finch's basic blocks for building a purely functional REST API backend
 * emulating a set of services working with ''users'' and their ''tickets'' (i.e., cinema tickets).
 *
 * The following packages represent the backend:
 *
 * - [[demo.model]] - domain model classes: `User` and `Ticket`
 * - [[demo.reader]] - [[io.finch.request.RequestReader]]s for models
 * - [[demo.service]] - the application services
 * - [[demo.endpoint]] - [[io.finch.route.Router]]s for services (endpoints)
 */
package object demo {

  import model._

  // A custom request type that wraps an `HttpRequest`.
  // We prefer composition over inheritance.
  case class AuthRequest(http: HttpRequest)

  // We define an implicit view from `AuthRequest to `HttpRequest`,
  // so we can get two benefits:
  //  1. We can treat an `Endpoint` as a `Service`, since it will be implicitly converted.
  //  2. We can treat an `AuthRequest` as ''HttpRequest'' and pass it to `RequestReader`.
  implicit val authReqEv = (req: AuthRequest) => req.http

  // A thread-safe ids generator.
  object Id {
    private val self = new AtomicLong(0)
    def apply(): Long = self.getAndIncrement
  }

  // An abstraction that represents an async interface to a database.
  object Db {
    // An underlying map.
    private val map = new ConcurrentHashMap[Long, User]().asScala

    def select(id: Long): Future[Option[User]] = map.get(id).toFuture
    def all: Future[Users] = new Users(map.values.toList).toFuture
    def insert(id: Long, u: User): Future[User] = {
      map += (id -> u)
      u.toFuture
    }
  }
}
