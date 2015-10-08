package io.finch.benchmarks.service

import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finagle.Service
import com.twitter.util.Future
import io.finch._
import io.finch.request._
import io.finch.response.EncodeResponse

class FinchUserService(implicit
  userDecoder: DecodeRequest[User],
  newUserInfoDecoder: DecodeRequest[NewUserInfo],
  userEncoder: EncodeResponse[User],
  usersEncoder: EncodeResponse[List[User]]
) extends UserService {

  val getUser: Endpoint[User] = get("users" / long) { id: Long =>
    Ok(db.get(id).flatMap {
      case Some(user) => Future.value(user)
      case None => Future.exception(UserNotFound(id))
    })
  }

  val getUsers: Endpoint[List[User]] = get("users") {
    Ok(db.all)
  }

  val postUser: Endpoint[Unit] = post("users" ? body.as[NewUserInfo]) { u: NewUserInfo =>
    for {
      id <- db.add(u.name, u.age)
    } yield Created.withHeader("Location" -> s"/users/$id")
  }

  val deleteUser: Endpoint[String] = delete("users") {
    for {
      count <- db.delete()
    } yield Ok(s"$count users deleted")
  }

  val putUser: Endpoint[Unit] = put("users" ? body.as[User]) { u: User =>
    NoContent(db.update(u))
  }

  val backend: Service[Request, Response] = (
    getUsers :+: getUser :+: postUser :+: deleteUser :+: putUser
  ).handle({
    case notFound @ UserNotFound(_) => BadRequest(notFound.getMessage)
  }).toService
}
