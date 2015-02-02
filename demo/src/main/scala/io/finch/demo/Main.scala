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

import com.twitter.finagle.{Filter, SimpleFilter, Service, Httpx}
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

// A custom request type that wraps an ''HttpRequest''.
// We prefer composition over inheritance.
case class AuthRequest(http: HttpRequest, secret: String)

// A companion object for ''AuthRequest'' that is required only to
// wrap an implicit value ''authReqEv''.
object AuthRequest {

  // We define an implicit view from ''AuthRequest'' to ''HttpRequest'',
  // so we can get two benefits:
  //  1. We can treat an ''Endpoint'' as a ''Service'', since it will be implicitly converted.
  //  2. We can treat an ''AuthRequest'' as ''HttpRequest'' and pass it to ''RequestReader''.
  implicit val authReqEv = (req: AuthRequest) => req.http
}

// Import an implicit view.
import AuthRequest._

// A simple base class for data classes.
trait ToJson { def toJson: Json }

// A companion object for ''ToJson'' used to define an implicit class
// ''SeqToJson'' that converts a service that returns a sequence of ''ToJson''
// objects into a service that returns ''ToJson'' object.
object ToJson {
  def seqToJson(seq: Seq[ToJson]) = new ToJson {
    def toJson = Json.arr(seq.map { _.toJson }: _*)
  }

  implicit class SeqToJson[A](s: Service[A, Seq[ToJson]]) extends Service[A, ToJson] {
    def apply(req: A) = s(req) map seqToJson
  }
}

// Import implicit class.
import ToJson._

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
case class GetUser(userId: Long, db: Main.Db) extends Service[AuthRequest, User] {
  def apply(req: AuthRequest) = db.get(userId) match {
    case Some(user) => user.toFuture
    case None => UserNotFound(userId).toFutureException
  }
}

// A REST service that fetches all users from the the database ''db''.
case class GetAllUsers(db: Main.Db) extends Service[AuthRequest, Seq[User]] {
  def apply(req: AuthRequest) = db.values.toSeq.toFuture
}

// A REST service that inserts a new user with ''userId'' into the database ''db''.
case class PostUser(userId: Long, db: Main.Db) extends Service[AuthRequest, User] {
  // A requests reader that reads user objects from the http request.
  // A user is represented by url-encoded param ''name''.
  val user: RequestReader[User] = for {
    name <- RequiredParam("name").should("be greater then 5 symbols"){ _.length > 5 }
  } yield User(userId, name, Seq.empty[Ticket])

  def apply(req: AuthRequest) = for {
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
case class PostUserTicket(userId: Long, ticketId: Long, db: Main.Db) extends Service[AuthRequest, Ticket] {
  // A request reader that reads ticket object from the http request.
  // A ticket object is represented by json object serialized in request body.
  val ticket: RequestReader[Ticket] = for {
    json <- RequiredBody[Json]
  } yield Ticket(ticketId, json[String]("label").getOrElse("N/A"))

  def apply(req: AuthRequest) = for {
    t <- ticket(req)
    u <- GetUser(userId, db)(req) // fetch exist user
    updatedU = u.copy(tickets = u.tickets :+ t) // modify its tickets
  } yield {
    db += (userId -> updatedU) // add new user into a mutable map
    t
  }
}

// A REST endpoint that routes user-specific requests.
object UserEndpoint extends Endpoint[AuthRequest, ToJson] {
  def route = {
    case Method.Get -> Root / "users" =>
      GetAllUsers(Main.Db)
    case Method.Get -> Root / "users" / Long(id) =>
      GetUser(id, Main.Db)
    case Method.Post -> Root / "users" => WithGeneratedId { id =>
      PostUser(id, Main.Db)
    }
  }
}

// A REST endpoint that routes ticket-specific requests.
object TicketEndpoint extends Endpoint[AuthRequest, ToJson] {
  def route = {
    case Method.Post -> Root / "users" / Long(userId) / "tickets" => WithGeneratedId { id =>
      PostUserTicket(userId, id, Main.Db)
    }
  }
}

// A Finagle filter that authorizes a request: performs conversion ''HttpRequest'' => ''AuthRequest''.
object Authorize extends Filter[HttpRequest, HttpResponse, AuthRequest, HttpResponse] {
  def apply(req: HttpRequest, service: Service[AuthRequest, HttpResponse]) = for {
    secret <- RequiredHeader("Secret")(req)
    rep <- if (secret == "open sesame") service(AuthRequest(req, secret))
           else Unauthorized(Json.obj("error" -> "wrong_secret")).toFuture
  } yield rep
}

// A simple Finagle filter that handles all the exceptions, which might be thrown by
// a request reader of one of the REST services.
object HandleExceptions extends SimpleFilter[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]) =
    service(req) handle {
      case UserNotFound(id) => BadRequest(Json.obj("error" -> "user_not_found", "id" -> id))
      case request.NotFound(item) => BadRequest(Json.obj("error" -> "item_not_found", "item" -> item))
      case np: NotParsed => BadRequest(Json.obj("error" -> "item_not_parsed", "item" -> np.item, "message" -> np.getMessage))
      case NotValid(item, rule) => BadRequest(Json.obj("error" -> "item_not_valid", "rule" -> rule))
      case _ => InternalServerError()
    }
}

/**
 * To run the demo from console use:
 *
 * > sbt 'project demo' 'run io.finch.demo.Main'
 */
object Main extends App {

  // A type that represents the database.
  type Db = mutable.Map[Long, User]

  // A database instance.
  val Db = new ConcurrentHashMap[Long, User]().asScala

  // An http endpoint that is composed of user and ticket endpoints.
  val httpBackend: Endpoint[AuthRequest, HttpResponse] =
    (UserEndpoint orElse TicketEndpoint) ! TurnModelIntoJson ! TurnIntoHttp[Json]

  // A backend endpoint with exception handler and Auth filter.
  val backend: Endpoint[HttpRequest, HttpResponse] =
    HandleExceptions ! Authorize ! httpBackend

  // A default Finagle service builder that runs the backend.
  Await.ready(Httpx.serve(new InetSocketAddress(8080), backend))
}
