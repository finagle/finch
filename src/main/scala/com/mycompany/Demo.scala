package com.mycompany

import com.twitter.finagle.http.path._
import com.twitter.finagle.{Filter, Service}
import com.twitter.util.Future
import com.twitter.finagle.http.Method
import io.finch.{HttpResponse, RestApi, TurnJsonToHttp, WrapJsonWithMetaAsTag, Resource, JsonObject, JsonArray, JsonResponse, HttpRequest}

object GetAllUsers extends Service[HttpRequest, JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    val rep = JsonArray(
      JsonObject("id" -> 10, "name" -> "Ivan"),
      JsonObject("id" -> 20, "name" -> "John")
    )
    Future.value(rep)
  }
}

class GetUserById(id: Long) extends Service[HttpRequest, JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    val rep = JsonObject("id" -> id, "name" -> "Simon")
    Future.value(rep)
  }
}

object User extends Resource {

  def route = {
    case Method.Get -> Root / "users" =>
      GetAllUsers afterThat WrapJsonWithMetaAsTag("users") afterThat TurnJsonToHttp
    case Method.Get -> Root / "users" / Long(id) =>
      new GetUserById(id) afterThat WrapJsonWithMetaAsTag("user") afterThat TurnJsonToHttp
  }
}

object UserApi extends RestApi {

  val authorize = Filter.identity[HttpRequest, HttpResponse]

  exposeAt(8080) {
    authorize andThen (User orElse User)
  }
}
