package io.finch.benchmarks.service

import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import io.finch._
import io.finch.request._
import io.finch.response._

class FinchUserService(implicit
  userDecoder: DecodeRequest[User],
  newUserInfoDecoder: DecodeRequest[NewUserInfo],
  userEncoder: EncodeResponse[User],
  usersEncoder: EncodeResponse[List[User]]
) extends UserService {

  val getUser: Endpoint[User] = get("users" / long) { id: Long =>
    db.get(id).flatMap {
      case Some(user) => Future.value(user)
      case None => Future.exception(UserNotFound(id))
    }
  }

  val getUsers: Endpoint[List[User]] = get("users") { db.all }

  val postUser: Endpoint[Response] = post("users" ? body.as[NewUserInfo]) { u: NewUserInfo =>
    db.add(u.name, u.age).map { id =>
      Created.withHeaders("Location" -> s"/users/$id")()
    }
  }

  val deleteUser: Endpoint[Response] = delete("users") {
    db.delete().map(count => Ok(s"$count users deleted"))
  }

  val putUser: Endpoint[Response] = put("users" ? body.as[User]) { u: User =>
    db.update(u).map(_ => NoContent())
  }

  val users: Service[Request, Response] = (
    getUsers :+: getUser :+: postUser :+: deleteUser :+: putUser
  ).toService

  val handleExceptions = new SimpleFilter[Request, Response] {
    def apply(req: Request, service: Service[Request, Response]): Future[Response] =
      service(req).handle {
        case notFound @ UserNotFound(_) => BadRequest(notFound.getMessage)
        case _ => InternalServerError()
      }
  }

  val backend = handleExceptions andThen users
}
