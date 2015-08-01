/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.benchmarks.service
package finagle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.httpx._
import com.twitter.finagle.httpx.Method._
import com.twitter.finagle.httpx.path._
import com.twitter.finagle.httpx.service.RoutingService
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Future

class FinagleUserService extends UserService {
  val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  val users = RoutingService.byMethodAndPathObject {
      case Get -> Root / "users" / Long(id) => Service.mk { (req: Request) =>
        db.get(id).flatMap {
          case Some(user) => Future.value {
            Response(
              req.version,
              Status.Ok,
              Reader.fromBuf(Buf.Utf8(mapper.writeValueAsString(user)))
            )
          }
          case None => Future.exception[Response](UserNotFound(id))
        }
      }
      case Get -> Root / "users" => Service.mk { (req: Request) =>
        db.all.map { (users: List[User]) =>
          Response(
            req.version,
            Status.Ok,
            Reader.fromBuf(Buf.Utf8(mapper.writeValueAsString(users)))
          )
        }
      }
      case Post -> Root / "users" => Service.mk { (req: Request) =>
        val user = mapper.readValue[NewUserInfo](req.contentString, classOf[NewUserInfo])
        db.add(user.name, user.age).map { id =>
          val response = Response(req.version, Status.Created)
          response.location = s"/users/$id"
          response
        }
      }
      case Put -> Root / "users" => Service.mk { (req: Request) =>
        val user = mapper.readValue[User](req.contentString, classOf[User])
        db.update(user).map { _ =>
          Response(req.version, Status.NoContent)
        }
      }
      case Delete -> Root / "users" => Service.mk { (req: Request) =>
        db.delete.map { (count: Int) =>
          Response(
            req.version,
            Status.Ok,
            Reader.fromBuf(Buf.Utf8(s"$count users deleted"))
          )
        }
      }
    }

  val handleExceptions = new SimpleFilter[Request, Response] {
    def apply(req: Request, service: Service[Request, Response]): Future[Response] =
      service(req).handle {
        case notFound @ UserNotFound(_) => Response(
          req.version,
          Status.BadRequest,
          Reader.fromBuf(Buf.Utf8(notFound.getMessage))
        )
        case _ => Response(req.version, Status.InternalServerError)
      }
  }

  val backend = handleExceptions andThen users
}

class FinagleBenchmark extends UserServiceBenchmark(() => userService)
