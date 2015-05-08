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

  val createUser: Service[HttpRequest, Long] = Service.mk { req =>
    for {
      NewUserInfo(name, age) <- body.as[NewUserInfo].apply(req)
      user <- db.add(name, age)
    } yield user
  }

  val updateUser: Service[HttpRequest, Unit] = Service.mk { req =>
    body.as[User].apply(req).flatMap(db.update)
  }

  def deleteUsers: Service[HttpRequest, Int] = Service.const(db.delete)

  def toHttp[A](f: A => HttpResponse): Service[A, HttpResponse] = Service.mk(a => f(a).toFuture)

  val users: Endpoint[HttpRequest, HttpResponse] =
    (Get / "users" / long /> getUser).map(_ ! toHttp(Ok(_))) |
    (Get / "users" /> allUsers).map(_ ! toHttp(Ok(_))) |
    (Post / "users" /> createUser).map(
      _ ! toHttp(id => Created.withHeaders("Location" -> s"/users/$id")())
    ) |
    (Put / "users" /> updateUser).map(_ ! toHttp(_ => NoContent())) |
    (Delete / "users" /> deleteUsers).map(_ ! toHttp(count => Ok(s"$count users deleted")))

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
