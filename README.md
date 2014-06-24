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
  "io" %% "finch" % "0.0.33"
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
    JsonArray(rep map { _.toJson }:_*).toFuture
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
    authorize andThen respond afterThat TurnJsonIntoHttp
  }
}
```

**Step 7:** Have fun and stay finagled!

Request Reader Monad
--------------------
**Finch.io** has two builtin request readers, which implement Reader Monad functional design pattern: 
* `FutureRequestReader` returning `Future[A]` and
* `RequestReader` returning `Option[A]`.

A `FutureRequestReader` has return type `Future[A]` so it might be simply used as an additional monad-transformation in a top-level for-comprehension statement. This is dramatically useful when service should fetch some params from a request before doing a real job (and not doing it at all if some of the params are not found/not valid).

There are three common implementaions of a `FutureRequestReader`:
* `RequiredParam` - fetches required params within specified type
* `OptionalParam` - fetches optional params
* `ValidationRule` - fails if given predicate is false

```scala
case class User(name: String, age: Int, city: String)

// Define a new request reader composed from provided out-of-the-box readers.
val remoteUser = for {
  name <- RequiredParam("name")
  age <- RequiredIntParam("age")
  city <- OptionalParam("c")
} yield User(name, age, city.getOrElse("Novosibirsk"))

val service = new Service[HttpRequest, JsonResponse] {
  def apply(req: HttpRequest) = for {
    user <- remoteUser(req)
  } yield JsonObject(
    "name" -> user.name, 
    "age" -> user.age, 
    "city" -> user.city
  )
}

val user = service(...) handle {
  case e: ParamNotFound => JsonObject("status" -> 400) // bad request
}
```

The most cool thing about monads is that they may be composed/reused as hell. Here is the example of _extending_ an existent reader within new fields/validation rules.

```scala
val restrictedUser = {
  user <- remoteUser
  _ <- ValidationRule("this an adults-only video") { user.age > 18 }
} yield user
```

The exceptions from a request-reader might be handled just like other future exceptions in Finagle:
```scala
val user = service(...) handle {
  case e: ParamNotFound => JsonObject("status" -> 400, "error" -> e.getMessage)
  case e: ValidationFailed => JsonObject("status" -> 400, "error" -> e.getMessage)
}
```

There is also very simple reader `Param` that may be used for optional params, which are not required for the service's logic.

```scala
val pagination = for {
  offsetId <- IntParam("offsetId")
  limit <- IntParam("limit")
} yield (
  offsetId.getOrElse(0),
  math.min(limit.getOrElse(50), 50)
)

val service = new Service[HttpRequest, JsonResponse] {
  def apply(req: HttpRequest) = {
    val (offsetIt, limit) = pagination(req)
    JsonObject.empty.toFuture
  }
}
```

*Note* that `FutureRequestReader` and `RequestReader` may not be composed together (in the same chain of transformations). So, if at least one param is required the composition of `RequiredParam`-s and `OptionalParam`-s should be used.

#### A `RequiredParam` reader makes sure that
* param is presented in the request (othervise it throws `ParamNoFound` exception)
* param is not empty (othervise it throws `ValidationFailed` exception)
* param may be converted to a requested type `RequiredIntParam`, `RequiredLongParam` or `RequiredBooleanParam` (othervise it throws `ValidationFailed` exception).

#### An `OptionalParam` returns 
* `Future[Some[A]]` if param is presented in the request and may be converted to a requested type `OptionalIntParam`, `OptionalLongParam` or `OptionalBooleanParam`
* `Future.None` otherwise.

#### A `Param` returns 
* `Some[A]` if param is presented in the request and may be converted to a requested type `IntParam`, `LongParam` or `BooleanParam`. 
* `None` otherwise.


#### A `ValidationRule(rule)(predicate)` 
* returns `Future.Done` when predicate is `true`
* throws `ValidationFailed` exception with `rule` stored in the message field.


### Multiple-Value Params
All the readers have companion readers that can read multiple-value params `List[A]` instead of single-value params `A`. Multiple-value readers have `s` postfix in their names. So, `Param` has `Params`, `OptionalParam` has `OptipnalParams` and finally `RequiredParam` has `RequiredParams` companions. There are also typed versions for every reader, like `IntParams` or even `OptionalLongParams`.

Thus, the following HTTP params `a=1,2,3&b=4&b=5` might be fetched with `IntParams` reader like this:

```scala
val reader = for {
 a <- IntParams(a)
 b <- IntParams(b)
} yield (a, b)

val (a, b): (List[Int], List[Int]) = reader(request)
// a = List(1, 2, 3)
// b = List(4, 5)
```


Bonus Track: JSON on Steroids
-----------------------------

**Finch.io** provides a slight API for working with standard classes `scala.util.parsing.json.JSONObject` and `scala.util.parsing.json.JSONArray`. The core methods and practices are described follow.

**JsonObject & JsonArray**
```scala
val a: JsonResponse = JsonObject("tagA" -> "valueA", "tagB" -> "valueB")
val b: JsonResponse = JsonObject("1" -> 1, "2" -> 2)
val c: JsonResponse = JsonArray(a, b, "string", 10) 
```

**Pattern Matching**
```scala
val a: JsonResponse = JsonObject.empty
val b: JsonResponse = a match {
  case JsonObject(oo) => oo // 'oo' is JSONObject
  case JsonArray(aa) => aa  // 'aa' is JSONArray
}
```

**Merging JSON objects**
```scala
// { a : { b : { c: { x : 10, y : 20, z : 30 } } }
val a = JsonObject("a.b.c.x" -> 10, "a.b.c.y" -> 20, "a.b.c.z" -> 30)

// { a : { a : 100, b : 200 } }
val b = JsonObject("a.a" -> 100, "a.b" -> 200)

// { 
//   a : { 
//     b : { c: { x : 10, y : 20, z : 30 } } 
//     a : 100
//   }
// }
val c = JsonObject.mergeLeft(a, b) // 'left' exposes a priority in conflicts-resolving

// { 
//   a : { 
//     a : 100
//     b : 200
//   }
// }
val d = JsonObject.mergeRight(a, b) // 'right' exposes a priority in conflicts-resolving
```

**Merging JSON arrays**
```scala
val a = JsonArray(1, 2, 3)
val b = JsonArray(4, 5, 6)

// [ 1, 2, 3, 4, 5, 6 ]
val c = JsonArray.concat(a, b)
```

**JsonObject Operations**
```scala
// { 
//   a : { 
//     x : 1,
//     y : 2.0f
//   }
// }
val o = JsonObject("a.x" -> 1, "a.y" -> 2.0f)

// get value by tag/path as Int
val oneB = o.get[Int]("a.x")

// get option of a value by tag/path as Float
val twoB = o.getOption[Float]("a.y")

// creates a new json object with function applied to its underlying map
val oo = o.within { _.take(2) }
```

**JsonArray Operations**
```scala
val a = JsonArray(JsonObject.empty, JsonObject.empty)

// creates a new json array with function applied to its underlying list
val aa = aa.within { _.take(5).distinct }
```
----
By Vladimir Kostyukov, http://vkostyukov.ru
