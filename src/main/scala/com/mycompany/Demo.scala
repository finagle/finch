package com.mycompany

import com.twitter.finagle.http.path._
import com.twitter.finagle.Filter
import com.twitter.util.Future
import com.twitter.finagle.http.Method
import io.finch._

object GetAllUsers extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    val rep = JsonArray(
      JsonObject("id" -> 10, "name" -> "Ivan"),
      JsonObject("id" -> 20, "name" -> "John")
    )
    Future.value(rep)
  }
}

class GetUserById(id: Long) extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    val rep = JsonObject("id" -> id, "name" -> "Simon")
    Future.value(rep)
  }
}

object User extends Resource {

  def route = {
    case Method.Get -> Root / "users" =>
      GetAllUsers afterThat TurnJsonToHttp
    case Method.Get -> Root / "users" / Long(id) =>
      new GetUserById(id) afterThat TurnJsonToHttp
  }
}

object UserApi extends RestApi {

  val authorize = Filter.identity[HttpRequest, HttpResponse]

  exposeAt(8080) {
    authorize andThen (User orElse User)
  }
}
