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

import _root_.argonaut.Argonaut._
import _root_.argonaut._
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.{Filter, Httpx, Service, SimpleFilter}
import com.twitter.util.{Await, Future}
import io.finch.{Endpoint => _, _}

// import the pipe `!` operator
import io.finch.argonaut._         // import finch-json classes such as `Json`
import io.finch.request._          // import request readers such as `RequiredParam`
import io.finch.request.items._    // import request items for error handling
import io.finch.response._         // import response builders such as `BadRequest`
import io.finch.route._            // import route combinators

/**
 * To run the demo from console use:
 *
 * > sbt 'project demo' 'run io.finch.demo.Main'
 *
 * Or just:
 *
 * > sbt demo/run
 *
 * You can then access the service using curl, for example:
 *
 * > # List users
 * > curl -i -H "X-Secret: open sesame" http://localhost:8080/users <--- X-Secret is a "header"!!
 * > # Get a user
 * > curl -i -H "X-Secret: open sesame" http://localhost:8080/users/1
 * > # Add a user
 * > curl -i -H "X-Secret: open sesame" -X POST http://localhost:8080/users?name=Foo%20McBar
 * > # Add a ticket for a user
 * > curl -i -H "X-Secret: open sesame" -d '{"label": "ORF -> DCA"}' http://localhost:8080/users/0/tickets
 */
object Main extends App {
  import model._

  val exampleUser1: User = User(Id(), "Tom Paine", Nil)

  val exampleUser2: User = User(
    Id(),
    "Benjamin Franklin",
    List(Ticket(Id(), "PHL -> CDG"), Ticket(Id(), "PHL -> LHR"))
  )

  val exampleUser3: User = User(
    Id(),
    "Betsy Ross",
    List(Ticket(Id(), "PHL -> DCA"))
  )

  val exampleUser4: User = User(
    Id(),
    "Sakata Gintoki",
    List(Ticket(Id(), "SFO -> SAN"))
  )

  // Pre-load the database with some examples.
  Await.ready(Db.insert(exampleUser1.id, exampleUser1))
  Await.ready(Db.insert(exampleUser2.id, exampleUser2))
  Await.ready(Db.insert(exampleUser3.id, exampleUser3))
  Await.ready(Db.insert(exampleUser4.id, exampleUser4))

  // Serve the backend using the Httpx protocol.
  val _ = Await.ready(Httpx.serve(":8080", Demo.backend))
}

object Demo {

  import endpoint._
  import model._

  val Secret = "open sesame"

  // A Finagle filter that authorizes a request: performs conversion `Request` => `AuthRequest`.
  val authorize = new Filter[Request, Response, AuthRequest, Response] {
    def apply(req: Request, service: Service[AuthRequest, Response]): Future[Response] = for {
      `X-Secret` <- headerOption("X-Secret")(req)
      rep <- `X-Secret` collect {
        case Secret => service(AuthRequest(req))
      } getOrElse Unauthorized().toFuture
    } yield rep
  }

  val handleDomainErrors: PartialFunction[Throwable, Response] = {
    case UserNotFound(id) => BadRequest(Json("error" := "user_not_found", "id" := id))
  }

  val handleRequestReaderErrors: PartialFunction[Throwable, Response] = {
    case NotPresent(ParamItem(p)) => BadRequest(
      Json("error" := "param_not_present", "param" := p)
    )

    case NotPresent(BodyItem) => BadRequest(Json("error" := "body_not_present"))

    case NotParsed(ParamItem(p), _, _) => BadRequest(
      Json("error" := "param_not_parsed", "param" := p)
    )

    case NotParsed(BodyItem, _, _) => BadRequest(Json("error" := "body_not_parsed"))

    case NotValid(ParamItem(p), rule) => BadRequest(
      Json("error" := "param_not_valid", "param" := p, "rule" := rule)
    )
  }

  val handleRouterErrors: PartialFunction[Throwable, Response] = {
    case RouteNotFound(route) => NotFound(Json("error" := "route_not_found", "route" := route))
  }

  // A simple Finagle filter that handles all the exceptions, which might be thrown by
  // a request reader of one of the REST services.
  val handleExceptions = new SimpleFilter[Request, Response] {
    def apply(req: Request, service: Service[Request, Response]): Future[Response] = service(req) handle
      (handleDomainErrors orElse handleRequestReaderErrors orElse handleRouterErrors orElse {
        case _ => InternalServerError()
      })
  }

  // An API endpoint.
  val api: Service[AuthRequest, Response] =
    (getUser :+: getUsers :+: postUser :+: postTicket).toService

  // An HTTP endpoint with exception handler and Auth filter.
  def backend: Service[Request, Response] =
    handleExceptions ! authorize ! api
}
