/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
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

import java.net.InetSocketAddress

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.{SimpleFilter, Service}
import com.twitter.finagle.http.{RichHttp, Http, Method}
import com.twitter.finagle.http.path._

import scala.collection.mutable
import scala.collection.JavaConverters._

import java.util.concurrent.ConcurrentHashMap

import io.finch._
import io.finch.json._
import io.finch.json.finch._
import io.finch.request._
import io.finch.response._
import io.finch.auth._

import scala.util.Random

trait ToJson { def toJson: Json }

case class Ticket(id: Long, label: String) extends ToJson {
  def toJson = Json.obj(
    "id" -> id,
    "label" -> label
  )
}
case class User(id: Long, name: String, tickets: Seq[Ticket]) extends ToJson {
  def toJson = Json.obj(
    "id" -> id,
    "name" -> name,
    "tickets" -> Json.arr(tickets.map(_.toJson) :_*)
  )
}

case class UserNotFound(userId: Long) extends Exception(s"User $userId is not found.")

object WithGeneratedId {
  private[this] val random = new Random()
  def apply[A](fn: Long => A): A = fn(random.nextLong())
}

case class GetUser(userId: Long, db: Main.Db) extends Service[HttpRequest, User] {
  def apply(req: HttpRequest) = db.get(userId) match {
    case Some(user) => user.toFuture
    case None => UserNotFound(userId).toFutureException
  }
}

case class PostUser(userId: Long, db: Main.Db) extends Service[HttpRequest, User] {
  val user: RequestReader[User] = for {
    name <- RequiredParam("name")
    _ <- ValidationRule("name", "should be greater then 5 symbols") { name.length > 5 }
  } yield User(userId, name, Seq.empty[Ticket])

  def apply(req: HttpRequest) = for {
    u <- user(req)
  } yield {
    db += (userId -> u)
    u
  }
}

object TurnModelIntoJson extends Service[ToJson, Json] {
  def apply(model: ToJson) = model.toJson.toFuture
}

case class PostUserTicket(userId: Long, ticketId: Long, db: Main.Db) extends Service[HttpRequest, Ticket] {
  val ticket: RequestReader[Ticket] = for {
    json <- RequiredJsonBody[Json]
  } yield Ticket(ticketId, json[String]("label").getOrElse("N/A"))

  def apply(req: HttpRequest) = for {
    t <- ticket(req)
    u <- GetUser(userId, db)(req)
    updatedU = u.copy(tickets = u.tickets :+ t)
  } yield {
    db += (userId -> updatedU)
    t
  }
}

object UserEndpoint extends Endpoint[HttpRequest, ToJson] {
  def route = {
    case Method.Get -> Root / "users" / Long(id) =>
      GetUser(id, Main.Db)
    case Method.Post -> Root / "users" => WithGeneratedId { id =>
      PostUser(id, Main.Db)
    }
  }
}

object TicketEndpoint extends Endpoint[HttpRequest, ToJson] {
  def route = {
    case Method.Post -> Root / "users" / Long(userId) / "tickets" => WithGeneratedId { id =>
      PostUserTicket(userId, id, Main.Db)
    }
  }
}

object HandleExceptions extends SimpleFilter[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]) =
    service(req) handle {
      case UserNotFound(id) => BadRequest(Json.obj("error" -> "user_not_found", "id" -> id))
      case ParamNotFound(param) => BadRequest(Json.obj("error" -> "param_not_found", "param" -> param))
      case ValidationFailed(param, rule) => BadRequest(Json.obj("error" -> "bad_param", "param" -> param, "rule" -> rule))
      case BodyNotFound => BadRequest(Json.obj("error" -> "body_not_found"))
      case JsonNotParsed => BadRequest(Json.obj("error" -> "json_not_parsed"))
      case _ => InternalServerError()
    }
}

/**
 * To run the demo from console use:
 *
 * > sbt 'project finch-demo' 'run io.finch.demo.Main'
 */
object Main extends App {

  type Db = mutable.Map[Long, User]

  val Db = new ConcurrentHashMap[Long, User]().asScala

  val httpBackend: Endpoint[HttpRequest, HttpResponse] =
    (UserEndpoint orElse TicketEndpoint) ! TurnModelIntoJson ! TurnJsonIntoHttp[Json]

  val backend: Endpoint[HttpRequest, HttpResponse] =
    BasicallyAuthorize("user", "password") ! HandleExceptions ! httpBackend

  ServerBuilder()
    .codec(RichHttp[HttpRequest](new Http()))
    .bindTo(new InetSocketAddress(8080))
    .name("finch-demo")
    .build(backend.toService)
}
