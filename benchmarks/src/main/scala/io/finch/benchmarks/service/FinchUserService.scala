package io.finch.benchmarks.service

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Service
import io.finch._

class FinchUserService(implicit
  userDecoder: DecodeRequest[User],
  newUserInfoDecoder: DecodeRequest[NewUserInfo],
  userEncoder: EncodeResponse[User],
  usersEncoder: EncodeResponse[List[User]],
  encodeErr: EncodeResponse[Map[String, String]]
) extends UserService {

  val getUser: Endpoint[User] = get("users" / long) { id: Long =>
    db.get(id).map {
      case Some(user) => Ok(user)
      case None => throw UserNotFound(id)
    }
  }

  val getUsers: Endpoint[List[User]] = get("users") {
    Ok(db.all)
  }

  val postUser: Endpoint[Unit] = post("users" ? body.as[NewUserInfo]) { u: NewUserInfo =>
    db.add(u.name, u.age).map(id => Created(()).withHeader("Location" -> s"/users/$id"))
  }

  val deleteUser: Endpoint[String] = delete("users") {
    db.delete().map(count => Ok(s"$count users deleted"))
  }

  val putUser: Endpoint[Unit] = put("users" ? body.as[User]) { u: User =>
    NoContent(db.update(u))
  }

  val backend: Service[Request, Response] = (
    getUsers :+: getUser :+: postUser :+: deleteUser :+: putUser
  ).handle({
    case e: UserNotFound => BadRequest(e)
    case e: Error => InternalServerError(e)
  }).toService
}
