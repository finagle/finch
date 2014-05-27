![logo](https://raw.github.com/vkostyukov/finch/master/finch-logo.png) 

Hi! I'm **Finch**, a super-tiny library (actually, just a single package-object
[io.finch](https://github.com/vkostyukov/finch/blob/master/src/main/scala/io/finch/package.scala))
atop of [Finagle](http://twitter.github.io/finagle) that makes the development of RESTFul
API services more pleasant and slick.

How to finagle your REST API with Finch?
----------------------------------------

**Step 1:** Add the dependency:

```
resolvers += "Finch.io" at "http://repo.konfettin.ru"

libraryDependencies ++= Seq(
  "io" %% "finch" % "0.0.26"
)
```

**Step 2:** Define your model (optional):
```scala
import io.finch._

trait Jsonable {
  def toJson: JsonResponse
}

case class User(id: Long, name: String) extends Jsonable {
  def toJson = JsonObject("id" -> id, "name" -> name)
}

case class Car(id: Long, manufacturer: String) extends Jsonable {
  def toJson = JsonObject("id" -> id, "manufacturer" -> manufacturer)
}
```

**Step 3:** Write your REST services:

```scala
import io.finch._

object GetAllUsers extends HttpServiceOf[Seq[User]] {
  def apply(req: HttpRequest) =
    List(User(10, "Ivan"), User(20, "John")).toFuture
}

class GetUserById(id: Long) extends HttpServiceOf[User] {
  def apply(req: HttpRequest) = User(id, "John").toFuture
}

class GetCarById(id: Long) extends HttpServiceOf[Car] {
  def apply(req: HttpRequest) = Car(id, "Toyota").toFuture
}
```

**Step 4:** Define your facets:

```scala
import io.finch._

object TurnObjectIntoJson extends Facet[Jsonable, JsonResponse] {
  def apply(rep: Jsonable) = rep.toJson.toFuture
}

object TurnCollectionIntoJson extends Facet[Seq[Jsonable], JsonResponse] {
  def apply(rep: Seq[Jsonable]) =
    JsonArray(rep map { _.toJson }).toFuture
}
```

**Step 5:** Define your resources using facets for data transformation:
```scala
import io.finch._

object User extends RestResourceOf[JsonResponse] {
  def route = {
    case Method.Get -> Root / "users" =>
      GetAllUsers afterThat TurnCollectionIntoJson
    case Method.Get -> Root / "users" / Long(id) =>
      new GetUserById(id) afterThat TurnObjectIntoJson
  }
}

object Car extends  RestResourceOf[JsonResponse] {
  def route = {
    case Method.Get -> Root / "cars" / Long(id) =>
      new GetCarById(id) afterThat TurnObjectIntoJson
  }
}
```

**Step 6:** Expose your resources with Finch instance:

```scala
import io.finch._

object Main extends RestApiOf[JsonResponse] {
  // We do nothing for now.
  val authorize = new SimpleFilter[HttpRequest, JsonResponse] {
    def apply(req: HttpRequest, continue: Service[HttpRequest, JsonResponse]) =
      continue(req)
  }

  def resource = User orElse Car

  // Expose the API at :8080.
  exposeAt(8080) { respond =>
    // 1. The ''respond'' value is a resource of JsonResponse,
    //    so we have to convert it to the resource of HttpResponse.
    // 2. Our REST API should be authorized.
    authorize afterThat respond afterThat TurnJsonIntoHttp
  }
}
```

**Step 7:** Have fun and stay finagled!

Bonus Track: JSON on Steroids
-----------------------------

**Finch.io** provides a slight asynchronous API for working with standard classes `scala.util.parsing.json.JSONObject` and `scala.util.parsing.json.JSONArray` from Finagle. The core methods and practices are described follow.

**JsonObject & JsonArray**
```scala
val repA: JsonResponse = JsonObject("tagA" -> "valueA", "tagB" -> "valueB")
val repB: JsonResponse = JsonObject("1" -> 1, "2" -> 2)
val repC: JsonResponse = JsonArray(Seq(repA, repB)) 
```

**Pattern Matching**
```scala
val repA: JsonResponse = JsonObject.empty
val repB: JsonResponse = repA match {
  case JsonObject(o) => o // 'o' is JSONObject
  case JsonArray(a) => a  // 'a' is JSONArray
}
```

**JsonObject Operations**
```scala
val o = JsonObject("1" -> 1, "2" -> 2.0f)

// get value by tag as String
val oneA = o("1")

// get value by tag as Int
val oneB = o.get[Int]("1")

// get option of a value by tag as Float
val twoB = o.getOption[Float]("2")
```

**JsonArray Operations**
```scala
val a = JsonArray(Seq(JsonObject.empty, JsonObject.empty))

// create a new json array with items mapped via pure function
val a1 = a map {
  case JsonObject(o) => o
  case JsonArray(_) => JsonObject.empty
}
```

----
By Vladimir Kostyukov, http://vkostyukov.ru
