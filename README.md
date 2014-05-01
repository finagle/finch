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
  "io" %% "finch" % "0.0.10"
)
```

**Step 4:** Write your REST services:

```scala
import io.finch._

case class User(id: Long, name: String)

object GetAllUsers extends HttpServiceOf[List[User]] {
  def apply(request: HttpRequest) =
    List(User(10, "Ivan"), User(20, "John")).toFuture
}

class GetUserById(id: Long) extends HttpServiceOf[User] {
  def apply(request: HttpRequest) = User(id, "John").toFuture
}
```

**Step 5:** Define your resources:

```scala
import io.finch._

object TurnUserIntoJson extends Facet[User, JsonResponse] {
  def apply(rep: User) = rep match {
    case User(id, name) => JsonObject("id" -> id, "name" -> name).toFuture
  }
}

object TurnUsersIntoJson extends Facet[List[User], JsonResponse] {
  def apply(rep: List[User]) =
    Future.collect(rep map TurnUserIntoJson) flatMap { seq =>
      JsonArray(seq:_*).toFuture
    }
}

object User extends RestResourceOf[JsonResponse] {
  def route = {
    case Method.Get -> Root / "users" =>
      GetAllUsers afterThat TurnUsersIntoJson
    case Method.Get -> Root / "users" / Long(id) =>
      new GetUserById(id) afterThat TurnUserIntoJson
  }
}

object Echo extends RestResource {
  def route = {
    case Method.Get -> Root / "echo" / String(what) =>
      new HttpService {
        def apply(req: HttpRequest) = {
          val rep = Response(Version.Http11, Status.Ok)
          rep.setContentString(what)

          rep.toFuture
        }
      }
  }
}
```

**Step 6:** Expose your resources with Finch instance:

```scala
import io.finch._

object Main extends RestApiOf[JsonResponse] {

  // We do nothing for now.
  val authorize = Filter.identity[HttpRequest, JsonResponse]

  // Core resources.
  def resource = User // orElse ThisResource orElse ThatResource

  // Expose the API at :8080.
  exposeAt(8080) { respond =>
    val main = authorize andThen respond afterThat TurnJsonIntoHttp
    main orElse Echo
  }
}

```

**Step 7:** Have fun and stay finagled!

----
By Vladimir Kostyukov, http://vkostyukov.ru
