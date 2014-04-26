Finch
=====

Hi! I'm **Finch**, a super-tiny library (actually, just a single package-object
[io.finch](https://github.com/vkostyukov/finch/blob/master/src/main/scala/io/finch/package.scala))
atop of [Finagle](http://twitter.github.io/finagle) that makes the development of RESTFul
API services more pleasant and slick.

How to finagle your REST API with Finch?
----------------------------------------

**Step 1:** Update your SBT dependencies:

```
libraryDependencies ++= Seq(
  "io" %% "finch" % "0.0.1"
)
```

**Step 2:** Write your REST services:

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
```

**Step 3:** Define your resources:

```scala
import io.finch._

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
```

**Step 4:** Expose your resoucers with Finch instance:

```scala
import io.finch._

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
