package io.finch.benchmarks.service

import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import io.finch.request._
import io.finch.response._
import io.finch.route._

class FinchUserService(implicit
  userDecoder: DecodeRequest[User],
  newUserInfoDecoder: DecodeRequest[NewUserInfo],
  userEncoder: EncodeResponse[User],
  usersEncoder: EncodeResponse[List[User]]
) extends UserService {
  def getUser(id: Long): Future[User] = db.get(id).flatMap {
    case Some(user) => Future.value(user)
    case None => Future.exception[User](UserNotFound(id))
  }

  val allUsers: Service[Request, List[User]] = Service.mk(_ => db.all)

  val createUser: Service[Request, Response] = Service.mk { req =>
    for {
      NewUserInfo(name, age) <- body.as[NewUserInfo].apply(req)
      id <- db.add(name, age)
    } yield Created.withHeaders("Location" -> s"/users/$id")()
  }

  val updateUser: Service[Request, Response] = Service.mk { req =>
    body.as[User].apply(req).flatMap(db.update).map(_ => NoContent())
  }

  val deleteUsers: Service[Request, Response] =
    Service.mk(_ => db.delete().map(count => Ok(s"$count users deleted")))

  val users: Service[Request, Response] = (
    get("users" / long) /> getUser :+:
    get("users") /> allUsers       :+:
    post("users") /> createUser    :+:
    put("users") /> updateUser     :+:
    delete("users") /> deleteUsers
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
