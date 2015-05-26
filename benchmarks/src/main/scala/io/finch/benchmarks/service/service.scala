package io.finch.benchmarks.service

import com.twitter.finagle.{Httpx, Service, SimpleFilter}
import com.twitter.finagle.httpx.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Closable, Future, Await}
import io.finch.{Endpoint => _, _}
import io.finch.request._
import io.finch.response._
import io.finch.route._

abstract class UserService {
  val db = new UserDb

  def backend: Service[HttpRequest, HttpResponse]
}

class FinchUserService(implicit
  userDecoder: DecodeRequest[User],
  newUserInfoDecoder: DecodeRequest[NewUserInfo],
  userEncoder: EncodeResponse[User],
  usersEncoder: EncodeResponse[List[User]]
) extends UserService {
  def getUser(id: Long): Service[HttpRequest, User] = Service.const(
    db.get(id).flatMap {
      case Some(user) => Future.value(user)
      case None => Future.exception[User](UserNotFound(id))
    }
  )

  def allUsers: Service[HttpRequest, List[User]] = Service.const(db.all)

  val createUser: Service[HttpRequest, HttpResponse] = Service.mk { req =>
    for {
      NewUserInfo(name, age) <- body.as[NewUserInfo].apply(req)
      id <- db.add(name, age)
    } yield Created.withHeaders("Location" -> s"/users/$id")()
  }

  val updateUser: Service[HttpRequest, HttpResponse] = Service.mk { req =>
    body.as[User].apply(req).flatMap(db.update).map(_ => NoContent())
  }

  def deleteUsers: Service[HttpRequest, HttpResponse] =
    Service.const(db.delete().map(count => Ok(s"$count users deleted")))

  val users: Endpoint[HttpRequest, HttpResponse] =
    Get    / "users" / long /> getUser :|:
    Get    / "users" /> allUsers       :|:
    Post   / "users" /> createUser     :|:
    Put    / "users" /> updateUser     :|:
    Delete / "users" /> deleteUsers

  val handleExceptions = new SimpleFilter[HttpRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]): Future[HttpResponse] =
      service(req).handle {
        case notFound @ UserNotFound(_) => BadRequest(notFound.getMessage)
        case NotPresent(_) => BadRequest("Missing parameter")
        case NotParsed(_, _, _) => BadRequest("Parameter not parsed")
        case _ => InternalServerError()
      }
  }

  val backend = handleExceptions andThen users
}
