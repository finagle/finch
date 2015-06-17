package io.finch

import io.finch.request.ToRequest

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import scala.collection.JavaConverters._

import com.twitter.util.Future

/**
 * The demo project shows the usage of Finch's basic blocks for building a purely functional REST API backend
 * emulating a set of services working with pet stores, pets, and users (who purchase the pets).
 *
 * The following packages represent the backend:
 *
 * - [[petstore.model]] - domain model classes: Pet, Store, User
 * - [[petstore.reader]] - [[io.finch.request.RequestReader]]s for models
 * - [[petstore.service]] - the application services
 * - [[petstore.endpoint]] - [[io.finch.route.Router]]s for services (endpoints)
 */

package petstoreDemo {

import com.twitter.util.Future
import io.finch._
import io.finch.demo.model
import io.finch.demo.model.User
import io.finch.request.ToRequest
import model._

// A custom request type that wraps an `HttpRequest`.
// We prefer composition over inheritance.
case class AuthRequest(http: HttpRequest)

object AuthRequest {
  implicit val toRequest: ToRequest[AuthRequest] =
    ToRequest[AuthRequest](_.http)
}

// A thread-safe ids generator.
object Id {
  private val self = new AtomicLong(0)
  def apply(): Long = self.getAndIncrement
}

// An abstraction that represents an async interface to a database of users.
object UserDb {
  // An underlying map.
  private val map = new ConcurrentHashMap[Long, User]().asScala

  def select(id: Long): Future[Option[User]] = map.get(id).toFuture
  def all: Future[List[User]] = map.values.toList.toFuture
  def insert(id: Long, u: User): Future[User] = {
    map += (id -> u)
    u.toFuture
  }
  def delete(id: Long): None = map.remove(id)
}

// An abstraction that represents an async interface to a database of users.
object PetDb {
  // An underlying map.
  private val map = new ConcurrentHashMap[Long, Pet]().asScala

  def select(id: Long): Future[Option[Pet]] = map.get(id).toFuture
  def all: Future[List[Pet]] = map.values.toList.toFuture
  def insert(id: Long, u: Pet): Future[Pet] = {
    map += (id -> u)
    u.toFuture
  }
  def delete(id: Long): None = map.remove(id)
}
}