Finch
=====

Hi! I'm **Finch**, a super-tiny library (actually, just a single package-object
[io.finch](https://github.com/vkostyukov/finch/blob/master/src/main/scala/io/finch/package.scala))
atop of [Finagle](http://twitter.github.io/finagle) that makes the development of RESTFul
API services more pleasant and slick.

How to finagle your REST API with Finch?
----------------------------------------

**Step 1:** Clone Finch:

```
git clone git@github.com:vkostyukov/finch.git
```

**Step 2:** Build Finch and publish it to your local repo:

```
sbt publishLocal
```

**Step 3:** Update your project dependencies:


```
libraryDependencies ++= Seq(
  "io" %% "finch" % "0.0.8"
)
```

**Step 4:** Write your REST services:

```scala
import io.finch._

object GetAllUsers extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest) = {
    JsonArray(
      JsonObject("id" -> 10, "name" -> "Ivan"),
      JsonObject("id" -> 20, "name" -> "John")
    ).toFuture
  }
}

class GetUserById(id: Long) extends HttpServiceOf[JsonResponse] {
  def apply(request: HttpRequest) = {
    JsonObject("id" -> id, "name" -> "Simon").toFuture
  }
}

class Echo(what: String) extends HttpService {
  def apply(request: HttpRequest) = {
    val rep = Response(Version.Http11, Status.Ok)
    rep.setContentString(msg)

    rep.toFuture
  }
}
```

**Step 5:** Define your resources:

```scala
import io.finch._

object User extends RestResource {
  def route = {
    case Method.Get -> Root / "users" => 
      GetAllUsers afterThat TurnJsonIntoHttp
    case Method.Get -> Root / "users" / Long(id) => 
      new GetUserById(id) afterThat TurnJsonIntoHttp
  }
}

object Echo extends RestResource {
  def route = {
    case Method.Get -> Root / "echo" / String(what) =>
      new Echo(what)
  }
}
```

**Step 6:** Expose your resources with Finch instance:

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

**Step 7:** Have fun and stay finagled!

----
By Vladimir Kostyukov, http://vkostyukov.ru
