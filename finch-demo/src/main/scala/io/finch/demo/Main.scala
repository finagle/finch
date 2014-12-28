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

import com.twitter.finagle.{SimpleFilter, Service}
import com.twitter.finagle.Httpx
import com.twitter.finagle.httpx.Method
import com.twitter.finagle.httpx.path._
import com.twitter.util.Await

import scala.util.Random
import scala.collection.mutable
import scala.collection.JavaConverters._

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

import io.finch._            // import ''Endpoint'' and pipe ''!'' operator
import io.finch.json._       // import finch-json classes such as ''Json''
import io.finch.request._    // import request readers such as ''RequiredParam''
import io.finch.response._   // import response builders such as ''BadRequest''
import io.finch.auth._       // import ''BasicallyAuthorize'' filter

// A simple base class for data classes.
trait ToJson { def toJson: Json }

// A ticket object with two fields: ''id'' and ''label''.
case class Ticket(id: Long, label: String) extends ToJson {
  def toJson = Json.obj(
    "id" -> id,
    "label" -> label
  )
}

// A user object with three fields: ''id'', ''name'' and a collection of ''tickets''.
case class User(id: Long, name: String, tickets: Seq[Ticket]) extends ToJson {
  def toJson = Json.obj(
    "id" -> id,
    "name" -> name,
    "tickets" -> Json.arr(tickets.map(_.toJson) :_*)
  )
}

// An exception that indicates missing user with id ''userId''.
case class UserNotFound(userId: Long) extends Exception(s"User $userId is not found.")

// A helper class that generates unique long ids.
object WithGeneratedId {
  private[this] val random = new Random()
  def apply[A](fn: Long => A): A = fn(random.nextLong())
}

// A REST service that fetches a user with ''userId'' from the database ''db''.
case class GetUser(userId: Long, db: Main.Db) extends Service[HttpRequest, User] {
  def apply(req: HttpRequest) = db.get(userId) match {
    case Some(user) => user.toFuture
    case None => UserNotFound(userId).toFutureException
  }
}

// A REST service that inserts a new user with ''userId'' into the database ''db''.
case class PostUser(userId: Long, db: Main.Db) extends Service[HttpRequest, User] {
  // A requests reader that reads user objects from the http request.
  // A user is represented by url-encoded param ''name''.
  val user: RequestReader[User] = for {
    name <- RequiredParam("name")
    _ <- ValidationRule("name", "should be greater then 5 symbols") { name.length > 5 }
  } yield User(userId, name, Seq.empty[Ticket])

  def apply(req: HttpRequest) = for {
    u <- user(req)
  } yield {
    db += (userId -> u) // add new user into a mutable map
    u
  }
}

// A helper service that turns a model object into json.
object TurnModelIntoJson extends Service[ToJson, Json] {
  def apply(model: ToJson) = model.toJson.toFuture
}

// A REST service that add a ticket to a given user ''userId''.
case class PostUserTicket(userId: Long, ticketId: Long, db: Main.Db) extends Service[HttpRequest, Ticket] {
  // A request reader that reads ticket object from the http request.
  // A ticket object is represented by json object serialized in request body.
  val ticket: RequestReader[Ticket] = for {
    json <- RequiredBody[Json]
  } yield Ticket(ticketId, json[String]("label").getOrElse("N/A"))

  def apply(req: HttpRequest) = for {
    t <- ticket(req)
    u <- GetUser(userId, db)(req) // fetch exist user
    updatedU = u.copy(tickets = u.tickets :+ t) // modify its tickets
  } yield {
    db += (userId -> updatedU) // add new user into a mutable map
    t
  }
}

// A REST endpoint that routes user-specific requests.
object UserEndpoint extends Endpoint[HttpRequest, ToJson] {
  def route = {
    case Method.Get -> Root / "users" / Long(id) =>
      GetUser(id, Main.Db)
    case Method.Post -> Root / "users" => WithGeneratedId { id =>
      PostUser(id, Main.Db)
    }
  }
}

// A REST endpoint that routes ticket-specific requests.
object TicketEndpoint extends Endpoint[HttpRequest, ToJson] {
  def route = {
    case Method.Post -> Root / "users" / Long(userId) / "tickets" => WithGeneratedId { id =>
      PostUserTicket(userId, id, Main.Db)
    }
  }
}

// A simple Finagle filter that handles all the exceptions, which might be thrown by
// a request reader of one of the REST services.
object HandleExceptions extends SimpleFilter[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]) =
    service(req) handle {
      case UserNotFound(id) => BadRequest(Json.obj("error" -> "user_not_found", "id" -> id))
      case ParamNotFound(param) => BadRequest(Json.obj("error" -> "param_not_found", "param" -> param))
      case ValidationFailed(param, rule) => BadRequest(Json.obj("error" -> "bad_param", "param" -> param, "rule" -> rule))
      case BodyNotFound => BadRequest(Json.obj("error" -> "body_not_found"))
      case BodyNotParsed => BadRequest(Json.obj("error" -> "body_not_parsed"))
      case _ => InternalServerError()
    }
}

/**
 * To run the demo from console use:
 *
 * > sbt 'project finch-demo' 'run io.finch.demo.Main'
 */
object Main extends App {

  // A type that represents the database.
  type Db = mutable.Map[Long, User]

  // A database instance.
  val Db = new ConcurrentHashMap[Long, User]().asScala

  // An http endpoint that is composed of user and ticket endpoints.
  val httpBackend: Endpoint[HttpRequest, HttpResponse] =
    (UserEndpoint orElse TicketEndpoint) ! TurnModelIntoJson ! TurnIntoHttp[Json]

  // A backend endpoint with exception handler and Basic HTTP Auth filter.
  val backend: Endpoint[HttpRequest, HttpResponse] =
    BasicallyAuthorize("user", "password") ! HandleExceptions ! httpBackend

  // A default Finagle service builder that runs the backend.
  val socket = new InetSocketAddress(8080)
  val server = Httpx.serve(socket, backend.toService)
  Await.ready(server)
}
