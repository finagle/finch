# Finch.io Documentation

* [User Guide](docs.md#user-guide)
* [Endpoints](docs.md#endpoints)
* [Requests](docs.md#requests)
  * [Request Reader](docs.md#request-reader-monad)
  * [Multiple-Value Params](docs.md#multiple-value-params)
  * [HTTP Headers](docs.md#http-headers)
* [Responses](docs.md)
  * [Building HTTP responses](docs.md#building-http-responses)
  * [HTTP Redirects](docs.md#redirects)
* [Authentication](docs.md#authentification)
  * [OAuth2](docs.md#authorization-with-oauth2)
  * [Basic Auth](docs.md#basic-http-auth)
* [JSON](docs.md#json)

----

## User Guide

**Step 1:** Define a model (optional):
```scala
case class User(id: Long, name: String)
case class Ticket(id: Long)
```

**Step 2:** Implement REST services:

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

**Step 3:** Define filters/services for data transformation (optional):
```scala
import io.finch._
import scala.util.parsing.json.{JSONArray, JSONType, JSONObject}

object JsonHelpers {
  implicit def asJson(x: JSONType): Json = new Json {
    override def toString(): String = x.toString
  }
}

import scala.util.parsing.json.{JSONArray, JSONObject}

object TurnModelIntoJson extends Service[Any, Json] {
  import JsonHelpers._
  def apply(req: Any) = {
    def turn(any: Any): Json = any match {
      case User(id, name) => JSONObject(Map("id" -> id, "name" -> name))
      case Ticket(id) => JSONObject(Map("id" -> id))
      case seq: Seq[Any] => JSONArray(seq.toList)
    }

    turn(req).toFuture
  }
}
```

**Step 4:** Define endpoints using services/filters for data transformation (optional):
```scala
import io.finch._
import io.finch.json._
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.path._

object User extends Endpoint[HttpRequest, Json] {
  def route = {
    case Method.Get -> Root / "users" / Long(id) =>
      GetUser(id) ! TurnModelIntoJson
  }
}

object Ticket extends Endpoint[HttpRequest, Json] {
  def route = {
    case Method.Get -> Root / "tickets" / Long(id) =>
      GetTicket(id) ! TurnModelIntoJson
    case Method.Get -> Root / "users" / Long(id) / "tickets" =>
      GetUserTickets(id) ! TurnModelIntoJson
  }
}
```

**Step 5:** Expose endpoints:

```scala
import io.finch._
import io.finch.json._
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.{Http, RichHttp}
import java.net.InetSocketAddress

object Main extends App {
  val endpoint = Endpoint.join(User, Ticket) ! TurnJsonIntoHttp
  val backend = endpoint orElse Endpoint.NotFound

  ServerBuilder()
    .codec(RichHttp[HttpRequest](Http()))
    .bindTo(new InetSocketAddress(8080))
    .name("user-and-ticket")
    .build(backend.toService)
}
```

## Endpoints

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

## Requests

### Request Reader Monad

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

val service = new Service[HttpRequest, Json] {
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
} yield (offset.getOrElse(0), math.min(limit.getOrElse(50), 50))

val service = new Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    (offsetIt, limit) <- pagination(req)
  } yield Ok(s"Fetching items $offset..${offset+limit}")
}
```

Optional params may be validated by using `Option.forAll(p)` function, which does exactly what's needed. If the option is `Some(value)` it checks whether or not the given predicate `p` is true for `value`. Otherwise (if the option is `None`) it just returns `true`.

```scala
val pagination = for {
  offset <- OptionalIntParam("offset")
  limit <- OptionalIntParam("limit")
  _ <- ValidationRule("offset", "should be positive") { offset.forAll(_ >= 0) }
  _ <- ValidationRule("limit", "should be positive") { limit.forAll(_ >= 0) }
  _ <- ValidationRule("limit", "should be greater then offset") { 
   (for { o <- offset; l <- limit} yield (l > o)).getOrElse(true)
  }
} yield (offset.getOrElse(0), math.min(limit.getOrElse(50), 50))
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

## Responses

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

## Authentication

### Authorization with OAuth2

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is 100% compatible with **Finch.io**.

### Basic HTTP Auth

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is supported out-of-the-box and implemented 
as `finch.io.auth.BasicallyAuthorize` filter.

```scala
object ProtectedEndpoint extends Endpoint[HttpRequest, HttpResponse] {
  def route = {
    case Method.Get -> Root / "users" => BasicallyAuthorize("user", "password") ! GetUsers
  }
}
```

## JSON

**Finch.io** provides a single `trait` for interacting with json called `Json`.
Any object that extends this `trait` must ensure that its `toString` method returns string of valid json representing the object it wraps.
As a result you can use any JSON serialization library you like and then plug it into **Finch.io**.
The example from above does just that with the Scala JSON library.

**Converting JSON into HTTP**

There is a magic service `io.finch.json.TurnJsonIntoHttp` that takes a `Json` and converts it into an `HttpResponse`. This applicable for both `Service` and `Endpoint`.

```scala
import io.finch.json._

val a: Service[HttpRequest, Json] = ???
val b: Service[HttpRequest, HttpResponse] = a ! TurnJsonIntoHttp
```
