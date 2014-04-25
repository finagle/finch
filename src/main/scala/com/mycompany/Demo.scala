package com.mycompany

import com.twitter.finagle.http.path._
import com.twitter.finagle.Filter
import com.twitter.util.Future
import com.twitter.finagle.http.{Status, Method}
import io.finch._

object GetAllUsers extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    val rep = JsonArray(
      JsonObject("id" -> 10, "name" -> "Ivan"),
      JsonObject("id" -> 20, "name" -> "John")
    )

    JsonObject(
      "status" -> Status.Ok.getCode,
      "users" -> rep
    ).toFuture
  }
}

class GetUserById(id: Long) extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    val rep = JsonObject("id" -> id, "name" -> "Simon")

    JsonObject(
      "status" -> Status.Ok.getCode,
      "user" -> rep
    ).toFuture
  }
}

object TurnJsonToHttpWithStatus extends TurnJsonToHttpWithStatusFrom("status")

object User extends Resource {

  def route = {
    case Method.Get -> Root / "users" =>
      GetAllUsers afterThat TurnJsonToHttpWithStatus
    case Method.Get -> Root / "users" / Long(id) =>
      new GetUserById(id) afterThat TurnJsonToHttpWithStatus
  }
}

object UserApi extends RestApi {

  val authorize = Filter.identity[HttpRequest, HttpResponse]

  exposeAt(8080) {
    authorize andThen (User orElse User)
  }
}
