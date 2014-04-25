package io.finch

import com.twitter.finagle.http.path._
import com.twitter.finagle.{Filter, Service}
import com.twitter.util.Future
import com.twitter.finagle.http.Method

object GetAllUsers extends Service[HttpRequest, JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    JsonArray(
      JsonObject("id" -> 10, "name" -> "Ivan"),
      JsonObject("id" -> 20, "name" -> "John")
    ).toFuture
  }
}

class GetUserById(id: Long) extends Service[HttpRequest, JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    JsonObject("id" -> id, "name" -> "Simon").toFuture
  }
}

object User extends Resource {

  def route = {
    case Method.Get -> Root / "users" => GetAllUsers afterThat TurnJsonToHttp
    case Method.Get -> Root / "users" / Long(id) => new GetUserById(id) afterThat TurnJsonToHttp
  }
}

object UserApi extends RestApi {

  val authorize = Filter.identity[HttpRequest, HttpResponse]

  exposeAt(8080) {
    authorize andThen (User orElse User)
  }
}
