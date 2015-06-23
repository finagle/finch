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
import io.finch.petstore.model._
import io.finch.request.ToRequest

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

// An abstraction that represents an async interface to a database of pets.
object PetDb {
  // An underlying map.
  private val map = new ConcurrentHashMap[Long, Pet]().asScala

  def select(id: Long): Future[Option[Pet]] = map.get(id).toFuture
  def all: Future[List[Pet]] = map.values.toList.toFuture
  def insert(id: Long, p: Pet): Future[Pet] = {
    map += (id -> p)
    p.toFuture
  }
  def delete(id: Long): None = map.remove(id)
}

// An abstraction that represents an async interface to a database of stores.
object StoreDb {
  // An underlying map.
  private val map = new ConcurrentHashMap[Long, Store]().asScala

  def select(id: Long): Future[Option[Store]] = map.get(id).toFuture
  def all: Future[List[Store]] = map.values.toList.toFuture
  def insert(id: Long, s: Store): Future[Store] = {
    map += (id -> s)
    s.toFuture
  }
  def delete(id: Long): None = map.remove(id)
}

object TagDb {
  // An underlying map.
  private val map = new ConcurrentHashMap[Long, Tag]().asScala

  def select(id: Long): Future[Option[Tag]] = map.get(id).toFuture
  def all: Future[List[Tag]] = map.values.toList.toFuture
  def insert(id: Long, t: Tag): Future[Tag] = {
    map += (id -> t)
    t.toFuture
  }
  def delete(id: Long): None = map.remove(id)
}

object CategoryDb {
  // An underlying map.
  private val map = new ConcurrentHashMap[Long, Category]().asScala

  def select(id: Long): Future[Option[Category]] = map.get(id).toFuture
  def all: Future[List[Category]] = map.values.toList.toFuture
  def insert(id: Long, c: Category): Future[Category] = {
    map += (id -> c)
    c.toFuture
  }
  def delete(id: Long): None = map.remove(id)
}

}