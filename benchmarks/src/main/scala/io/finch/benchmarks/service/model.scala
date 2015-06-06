package io.finch.benchmarks.service

import com.twitter.util.Future
import scala.collection.mutable

case class Status(message: String)
case class User(id: Long, name: String, age: Int, statuses: List[Status])
case class NewUserInfo(name: String, age: Int)
case class UserNotFound(id: Long) extends Exception(s"No user $id")

class UserDb {
  private[this] val users = mutable.Map.empty[Long, User]

  def get(id: Long): Future[Option[User]] = Future.value(users.synchronized(users.get(id)))

  def add(name: String, age: Int): Future[Long] = Future.value(
    users.synchronized {
      val id = users.size.toLong
      users(id) = User(id, name, age, Nil)
      id
    }
  )

  def update(user: User): Future[Unit] = Future.value(users.synchronized(users(user.id) = user))

  def all: Future[List[User]] = Future.value(users.synchronized(users.values.toList.sortBy(_.id)))

  def delete(): Future[Int] = Future.value(
    users.synchronized {
      val count = users.size
      users.clear()
      count
    }
  )
}
