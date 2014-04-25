Finch
=====

Finch is a tiny library atop of [Finagle](http://http://twitter.github.io/finagle/) that allows you to build scalable
RESTful HTTP API backend.

An example usage looks as follows:

```scala
import io.finch._

object GetAllUsers extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    JsonArray(
      JsonObject("id" -> 10, "name" -> "Ivan"),
      JsonObject("id" -> 20, "name" -> "John")
    ).toFuture
  }
}

class GetUserById(id: Long) extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest): Future[JsonResponse] = {
    JsonObject("id" -> id, "name" -> "Simon").toFuture
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

object Echo extends Resource {
  def route = {
    case Method.Get -> Root / "echo" / String(msg) => 
      new HttpService {
        def apply(req: HttpRequest): Future[HttpResponse] = {
          val rep = Response(Version.Http11, Status.Ok)
          rep.setContentString(msg)

          Future.value(rep)
        }
      }
  }
}


object Main extends RestApi {
  // We do nothing for now.
  val authorize = Filter.identity[HttpRequest, HttpResponse]

  // Expose the API at :8080.
  exposeAt(8080) {
    authorize andThen (User orElse Echo)
  }
}

```
----
By Vladimir Kostyukov, http://vkostyukov.ru
