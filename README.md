![logo](https://raw.github.com/finagle/finch/master/finch-logo.png) 

Hi! I'm **Finch.io**, a thin layer of purely functional basic blocks atop of  [Finagle](http://twitter.github.io/finagle) for building robust and composable REST APIs.

Quickstart
----------

```scala
def hello(name: String) = new Service[HttpRequest, HttpResponse] = {
  def apply(req: HttpRequest) = for {
    title <- OptionalParam("title")(req)
  } yield Ok(s"Hello, ${title.getOrElse("")} $name!")
}

val endpoint = new Endpoint[HttpRequest, HttpResponse] {
  def route = {
    // routes requests like '/hello/Bob?title=Mr.'
    case Method.Get -> Root / "hello" / name => hello(name)
  }
}
```

A Hacker's Guide to Purely Functional API
-----------------------------------------

**Step 1:** Add the dependency:

```scala
resolvers += "Finch.io" at "http://repo.konfettin.ru"

libraryDependencies ++= Seq(
  "io" %% "finch" % "0.1.5"
)
```

**Step 2:** Define a model (optional):
```scala
case class User(id: Long, name: String)
case class Ticket(id: Long)
```

**Step 3:** Implement REST services:

```scala
import io.finch._
import com.twitter.finagle.Service

case class GetUser(userId: Long) extends Service[HttpRequest, User] {
  def apply(req: HttpRequest) = User(userId, "John").toFuture
}

case class GetTicket(ticketId: Long) extends Service[HttpRequest, Ticket] {
  def apply(req: HttpRequest) = Ticket(ticketId).toFuture
}

case class GetUserTickets(userId: Long) extends Service[HttpRequest, Seq[Ticket]] {
  def apply(req: HttpRequest) = Seq(Ticket(1), Ticket(2), Ticket(3)).toFuture
}
```

**Step 4:** Define filters/services for data transformation (optional):
```scala
import io.finch._
import io.finch.json._

object TurnModelIntoJson extends Service[Any, JsonResponse] {
  def apply(req: Any) = {
    def turn(any: Any): JsonResponse = any match {
      case User(id, name) => JsonObject("id" -> id, "name" -> name)
      case Ticket(id) => JsonObject("id" -> id)
      case seq: Seq[Any] => JsonArray(seq map turn :_*)
    }

    turn(req).toFuture
  }
}
```

**Step 5:** Define endpoints using services/filters for data transformation:
```scala
import io.finch._
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.path._

object User extends Endpoint[HttpRequest, JsonResponse] {
  def route = {
    case Method.Get -> Root / "users" / Long(id) => 
      GetUser(id) ! TurnModelIntoJson
  }
}

object Ticket extends Endpoint[HttpRequest, JsonResponse] {
  def route = {
    case Method.Get -> Root / "tickets" / Long(id) =>
      GetTicket(id) ! TurnModelIntoJson
    case Method.Get -> Root / "users" / Long(id) / "tickets" =>
      GetUserTickets(id) ! TurnModelIntoJson
  }
}
```

**Step 6:** Expose endpoints:

```scala
import io.finch._
import io.finch.json._
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.{Http, RichHttp}
import java.net.InetSocketAddress

object Main extends App {
  val endpoint = Endpoint.join(User, Ticket) ! TurnJsonIntoHttp
  val backend = BasicallyAuthorize("user", "password") ! (endpoint orElse Endpoint.NotFound)

  ServerBuilder()
    .codec(RichHttp[HttpRequest](Http()))
    .bindTo(new InetSocketAddress(8080))
    .name("user-and-ticket")
    .build(backend.toService)
}
```

**Step 7:** Have fun and stay finagled!

Piping the Requests/Responses
-----------------------------

The core operation in Finch.io is _pipe_ `!`. Both requests and responses may be piped via chain of filters, services or endpoints: the data flows in a natural direction - from left to right.

In the following example

- the request flows to `Auth` filter and then
- the request is converted to response by `respond` endpoint and then
- the response flows to `TurnJsonIntoHttp` service

```scala
val respond: Endpoint[HttpRequest, HttpResponse] = ???
val endpoint = Auth ! respond ! TurnJsonIntoHttp
```

The best practices on what to choose for data transformation are following

* Services should be used when the request is not required for the transformation.
* Otherwise, pure filters should be used.

Request Reader Monad
--------------------
**Finch.io** has built-in request reader that implement the [Reader Monad](http://www.haskell.org/haskellwiki/All_About_Monads#The_Reader_monad) functional design pattern: 
* `io.finch.request.RequestReader` reading `Future[A]`

A `RequestReader` has return type `Future[A]` so it might be simply used as an additional monad-transformation in a top-level for-comprehension statement. This is dramatically useful when a service should fetch some params from a request before doing a real job (and not doing it at all if some of the params are not found/not valid).

The following readers are available in Finch.io:
* `io.finch.request.EmptyReader` - throws an exception instead of reading 
* `io.finch.request.ConstReader` - fetches a const value from the request
* `io.finch.request.RequiredParam` - fetches required params within specified type
* `io.finch.request.OptionalParam` - fetches optional params within specified type
* `io.finch.request.RequiredParams` - fetches required multi-value params into the list
* `io.finch.request.OptionalParams` - fetches optional multi-value params into the list
* `io.finch.request.ValidationRule` - fails if given predicate is false

```scala
case class User(name: String, age: Int, city: String)

// Define a new request reader composed from provided out-of-the-box readers.
val user = for {
  name <- RequiredParam("name")
  age <- RequiredIntParam("age")
  city <- OptionalParam("city")
} yield User(name, age, city.getOrElse("Novosibirsk"))

val service = new Service[HttpRequest, JsonResponse] {
  def apply(req: HttpRequest) = for {
    u <- user(req)
  } yield JsonObject(
    "name" -> u.name, 
    "age" -> u.age, 
    "city" -> u.city
  )
}

val user = service(...) handle {
  case e: ParamNotFound => JsonObject("status" -> 400) // bad request
}
```

The most cool thing about monads is that they may be composed/reused as hell. Here is an example of _extending_ an existing reader with new fields/validation rules.

```scala
val restrictedUser = for {
  u <- user
  _ <- ValidationRule("age", "should be greater then 18") { user.age > 18 }
} yield user
```

The exceptions from a request-reader might be handled just like other future exceptions in Finagle:
```scala
val user = service(...) handle {
  case e: ParamNotFound => JsonObject("status" -> 400, "error" -> e.getMessage, "param" -> e.param)
  case e: ValidationFailed => JsonObject("status" -> 400, "error" -> e.getMessage, "param" -> e.param)
}
```

Optional params are quite often used for fetching pagination details.
```scala
val pagination = for {
  offset <- OptionalIntParam("offset")
  limit <- OptionalIntParam("limit")
} yield (offsetId.getOrElse(0), math.min(limit.getOrElse(50), 50))

val service = new Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    (offsetIt, limit) <- pagination(req)
  } yield Ok(s"Fetching items $offset..${offset+limit}")
}
```
#### A `io.finch.requests.RequiredParam` reader makes sure that
* param is presented in the request (otherwise it throws `ParamNoFound` exception)
* param is not empty (otherwise it throws `ValidationFailed` exception)
* param may be converted to a requested type `RequiredIntParam`, `RequiredLongParam` or `RequiredBooleanParam` (otherwise it throws `ValidationFailed` exception).

#### An `io.finch.request.OptionalParam` returns 
* `Future[Some[A]]` if param is presented in the request and may be converted to a requested type `OptionalIntParam`, `OptionalLongParam` or `OptionalBooleanParam`
* `Future.None` otherwise.

#### A `io.finch.request.ValidationRule(param, rule)(predicate)` 
* returns `Future.Done` when predicate is `true`
* throws `ValidationFailed` exception with `rule` and `param` fields

### Multiple-Value Params
All the readers have companion readers that can read multiple-value params `List[A]` instead of single-value params `A`. Multiple-value readers have `s` postfix in their names. So, `Param` has `Params`, `OptionalParam` has `OptipnalParams` and finally `RequiredParam` has `RequiredParams` companions. There are also typed versions for every reader, like `IntParams` or even `OptionalLongParams`.

Thus, the following HTTP params `a=1,2,3&b=4&b=5` might be fetched with `RequiredIntParams` reader like this:

```scala
val reader = for {
 a <- RequiredIntParams("a")
 b <- RequiredIntParams("b")
} yield (a, b)

val (a, b): (List[Int], List[Int]) = reader(request)
// a = List(1, 2, 3)
// b = List(4, 5)
```

### HTTP Headers
The HTTP headers may also be read with `RequestReader`. The following pre-defined readers should be used:
* `io.finch.request.RequiredHeader` - fetches header or throws `HeaderNotFound` exception
* `io.finch.request.OptionalHeader` - fetches header into an `Option`

Building HTTP responses
-----------------------

An entry point into the construction of HTTP responses in **Finch.io** is the `io.finch.response.Respond` class. It supports building of three types of responses: 
* `application/json` within JSON object in the response body
* `plain/text` within string in the response body
* empty response of given HTTP status

The common practice of using the `Respond` class is following:

```scala
import io.finch.json._
import io.finch.response._

val a = Respond(Status.Ok)() // an empty response with status 200
val b = Respond(Status.NotFound)("body") // 'plain/text' response with status 404
val c = Respond(Status.Created)(JsonObject("id" -> 42)) // 'application/json' response with status 201
```

HTTP headers may be added to respond instance with `withHeaders()` method:

```scala
val ok: Respond = Ok.withHeaders("Some-Header-A" -> "a", "Some-Header-B" -> "b")
val rep: HttpResponse = ok(JsonObject("a" -> 10))
```

There are also ten predefined factory objects for the [most popular HTTP statuses](http://www.restapitutorial.com/httpstatuscodes.html):

* `Ok`
* `Created`
* `NoContent`
* `MovedPermanently`
* `SeeOther`
* `NotModified`
* `TemporaryRedirect`
* `BadRequest`
* `Unauthorized`
* `PaymentRequired`
* `Forbidden`
* `NotFound`
* `MethodNotAllowed`
* `NotAcceptable`
* `RequestTimeOut`
* `Conflict`
* `PreconditionFailed`
* `TooManyRequests`
* `InternalServerError`
* `NotImplemented`

They may be used as follows:

```scala
import io.finch._
import io.finch.request._
import io.finch.response._

object Hello extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    name <- RequiredParam("name")(req)
  } yield Ok(s"Hello, $name!")
}
```

### Redirects

There is a tiny factory object `io.finch.response.Redirect` that may be used for generation redirect services. 
Here is the example:
 
```scala
val e = new Endpoint[HttpRequest, HttpResponse] = {
  def route = {
    case Method.Get -> Root / "users" / name => GetUser(name)
    case Method.Get -> Root / "Bob" => Redirect("/users/Bob") // or with path object
  }
}
```

Bonus Track: JSON on Steroids
-----------------------------

**Finch.io** provides a slight API for working with standard classes `scala.util.parsing.json.JSONObject` and `scala.util.parsing.json.JSONArray`. The API is consolidated in two classes `io.finch.json.JsonObject` and `io.finch.json.JsonArray`. The core methods and practices are described follow.

**JsonObject & JsonArray**
```scala
val a: JsonResponse = JsonObject("tagA" -> "valueA", "tagB" -> "valueB")
val b: JsonResponse = JsonObject("1" -> 1, "2" -> 2)
val c: JsonResponse = JsonArray(a, b, "string", 10) 
```

By default, `JsonObject` creates a _full_ json object (an object with null-value parameters).

```scala
val o = JsonObject("a.b.c" -> null)
```

A _full_ json object might be converted to a _compact_ json object (an object with only significant properties) by calling `compated` method on json object instance:

```scala
val o = JsonObject("a.b.c" -> null).compacted // will return an empty json object
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

**Converting JSON into HTTP**

There is a magic service `io.finch.json.TurnJsonIntoHttp` that takes a `JsonResponse` and converts it into an `HttpResponse`. This applicable for both `Service` and `Endpoint`.

```scala
import io.finch.json._

val a: Service[HttpRequest, JsonResponse] = ???
val b: Service[HttpRequest, HttpResponse] = a ! TurnJsonIntoHttp
```

Authorization with OAuth2
-------------------------

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is 100% compatible with **Finch.io**.

Basic HTTP Auth
---------------

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is supported out-of-the-box and implemented 
as `finch.io.auth.BasicallyAuthorize` filter.

```scala
object ProtectedEndpoint extends Endpoint[HttpRequest, HttpResponse] {
  def route = {
    case Method.Get -> Root / "users" => BasicallyAuthorize("user", "password") ! GetUsers
  }
}
```

Licensing
---------

The licensing details may be found at `LICENSE` file in the root directory.

----
By Vladimir Kostyukov, http://vkostyukov.ru
