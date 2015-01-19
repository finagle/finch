# Finch Documentation

* [Demo](docs.md#demo)
* [Endpoints](docs.md#endpoints)
* [Requests](docs.md#requests)
  * [Request Reader](docs.md#request-reader-monad)
  * [Query String Params](docs.md#query-string-params)
  * [Param Validation](docs.md#param-validation)
  * [Multiple-Value Params](docs.md#multiple-value-params)
  * [HTTP Headers](docs.md#http-headers)
  * [HTTP Bodies](docs.md#http-bodies)
* [Responses](docs.md#responses)
  * [Response Builder](docs.md#response-builder)
  * [HTTP Redirects](docs.md#redirects)
* [Authentication](docs.md#authentication)
  * [OAuth2](docs.md#authorization-with-oauth2)
  * [Basic Auth](docs.md#basic-http-auth)
* [JSON](docs.md#json)
  * [Finch Json](docs.md#finch-json)
  * [Argonaut](docs.md#argonaut)
  * [Jawn](docs.md#jawn)

----

## Demo

There is a single-file _demo_ project `finch-demo`, which is a complete REST API backend written with `finch-core` and
`finch-json` modules. The source code of the demo project is altered with useful comments that explain how to use its
building blocks such as `Endpoint`, `RequestReader`, `ResponseBuilder`, etc. The `finch-demo` module is just a single
Scala file [Main.scala][1] that is worth reading.

The following command may be used to run the demo:

```bash
sbt 'project demo' 'run io.finch.demo.Main'
```

## Endpoints

One of the most powerful abstractions in Finch is an `Endpoint`, which is a composable router. At the high level
it might be treated as a usual `PartialFunction` from request to service. Endpoints may be converted to Finagle services.
And more importantly they can be composed with other building blocks like filters, services or endpoints itself.

The core operator in Finch is _pipe_ (bang) `!` operator, which is like a Linux pipe exposes the data flow. Both
requests and responses may be piped via chain building blocks (filters, services or endpoints) in exact way it has been
written.

The common sense of using the Finch library is to have an `Endpoint` representing the domain. For example, the typical
use case would be to have an `Endpoint` from `OAuth2Request` (see [OAuth2 section](docs.md#authorization-with-oauth2))
to `Json` (see [Json section](docs.md#finch-json)). Since, all the endpoints have the same type (i.e.,
`Endpoint[OAuth2Request, Json]`) they may be composed together into a single entry point with either `Endpoint.join()`
or `orElse` operators. The following example shows the discussed example in details:


```scala
val auth: Filter[HttpRequest, HttpResponse, OAuth2Request, HttpResponse] = ???
val users: Endpoint[OAuth2Request, Json] = ???
val groups: Endpoint[OAuth2Request, Json] = ???
val endpoint: Endpoint[OAuth2Request, HttpResponse] = (users orElse groups) ! TurnIntoHttp[Json]

// An HTTP endpoint that may be served with `Httpx`
val httpEndpoint: Endpoint[HttpRequest, HttpResponse] = auth ! endpoint
```

The best practices on what to choose for data transformation are following:

* Services should be used when the request is not required for the transformation.
* Otherwise, pure Finagle filters should be used.

## Requests

### Request Reader Monad

Finch has built-in request reader that implements the [Reader Monad][2] functional design pattern:
* `io.finch.request.RequestReader` reads `Future[A]`

The simplified signature of the `RequestReader` abstraction is similar to `Service` but with monadic API methods `map`
and `flatMap`:

```scala
trait RequestReader[A] {
  def apply(req: HttpRequest): Future[A]
  def map[B](fn: A => B): RequestReader[B] = ???
  def flatMap(fn: A => RequestReader[B]): RequestReader[B] = ???
}
```

Since the request readers read futures they might be chained together with regular Finagle services in a single
for-comprehension. Thus, reading the request params is an additional monad-transformation in the program's data flow.
This is an extremely useful when a service should fetch and validate the request params before doing a real job and not
doing the job at all if the params are not valid. Request reader might throw a future exception and none of further
transformations will be performed. Reader Monad is sort of famous abstraction that is heavily used in Finch.

### Query String Params

The following readers are available in Finch:
* `io.finch.request.EmptyReader` - throws an exception instead of reading
* `io.finch.request.ConstReader` - fetches a const value from the request
* `io.finch.request.RequiredParam` - fetches required params within specified type
* `io.finch.request.OptionalParam` - fetches optional params within specified type
* `io.finch.request.RequiredParams` - fetches required multi-value params into the list
* `io.finch.request.OptionalParams` - fetches optional multi-value params into the list

```scala
case class User(name: String, age: Int, city: String)

// Define a new request reader composed from provided out-of-the-box readers.
val user: RequestReader[User] = for {
  name <- RequiredParam("name")
  age <- RequiredIntParam("age")
  city <- OptionalParam("city")
} yield User(name, age, city.getOrElse("Novosibirsk"))

val service = new Service[HttpRequest, Json] {
  def apply(req: HttpRequest) = for {
    u <- user(req)
  } yield Json.obj(
    "name" -> u.name,
    "age" -> u.age,
    "city" -> u.city
  )
}

val user: Json = service(req) handle {
  case e: ParamNotFound => Json.obj("status" -> 400) // bad request
}
```

Optional request readers such as `OptionalIntParam` are quite often used for fetching pagination details.

```scala
val pagination: RequestReader[(Int, Int)] = for {
  offset <- OptionalIntParam("offset")
  limit <- OptionalIntParam("limit")
} yield (offset.getOrElse(0), math.min(limit.getOrElse(50), 50))

val service = new Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    (offsetIt, limit) <- pagination(req)
  } yield Ok(s"Fetching items $offset..${offset+limit}")
}
```

#### A `io.finch.request.RequiredParam` reader makes sure that
* param is presented in the request (otherwise it throws `ParamNoFound(param)` exception)
* param is not empty (otherwise it throws `ValidationFailed(param, rule)` exception)
* param may be converted to a requested type `RequiredIntParam`, `RequiredLongParam` or `RequiredBooleanParam`
(otherwise it throws `ValidationFailed(param, rule)` exception).

#### An `io.finch.request.OptionalParam` returns
* `Future[Some[A]]` if param is presented in the request and may be converted to a requested type `OptionalIntParam`,
`OptionalLongParam` or `OptionalBooleanParam`
* `Future.None` otherwise.

### Param Validation

There is a request reader `ValidationRule` that exposes the validation logic. Since the request reader has monadic API, a for-comprehension might be used to compose request readers together. Here is an example of _composing_ an existing reader `user` within a `ValidationRule`, which is a primitive request that returns `Future.Done` if the given `predicate` is true or future of `ValidationFailed(param, rule)` exception otherwise.

```scala
val adult: RequestReader[User] = for {
  u <- user
  _ <- ValidationRule("age", "should be greater then 18") { user.age > 18 }
} yield u
```

The exceptions from a request-reader might be handled just like other future exceptions in Finagle:
```scala
val user: Future[Json] = service(...) handle {
  case ParamNotFound(param) => Json.obj("error" -> "param_not_found", "param" -> param)
  case ValidationFailed(param, rule) => Json.obj("error" -> "valudation_failed", "param" -> param, "rule" -> rule)
}
```

Note, that all the exception throw by `RequestReader` are case classes. So, the pattern matching my be used to handle them.

Optional params may be validated by using `Option.forAll(p)` function, which does exactly what's needed. If the option is `Some(value)` it checks whether or not the given predicate `p` is true for `value`. Otherwise (if the option is `None`) it just returns `true`.

```scala
val pagination: RequestReader[(Int, Int)] = for {
  offset <- OptionalIntParam("offset")
  limit <- OptionalIntParam("limit")
  _ <- ValidationRule("offset", "should be positive") { offset.forAll(_ >= 0) }
  _ <- ValidationRule("limit", "should be positive") { limit.forAll(_ >= 0) }
  _ <- ValidationRule("limit", "should be greater then offset") {
   (for { o <- offset; l <- limit} yield (l > o)).getOrElse(true)
  }
} yield (offset.getOrElse(0), math.min(limit.getOrElse(50), 50))
```

#### A `io.finch.request.ValidationRule(param, rule)(predicate)`
* returns `Future.Done` when predicate is `true`
* throws `ValidationFailed(param, rule)` exception

### Multiple-Value Params

All the readers have companion readers that can read multiple-value params `List[A]` instead of single-value params `A`.
Multiple-value readers have `s` postfix in their names. So, `Param` has `Params`, `OptionalParam` has `OptionalParams`
and finally `RequiredParam` has `RequiredParams` companions. There are also typed versions for every reader, like
`IntParams` or even `OptionalLongParams`.

Thus, the following HTTP params `a=1,2,3&b=4&b=5` might be fetched with `RequiredIntParams` reader like this:

```scala
val reader: RequestReader[(Int, Int)] = for {
 a <- RequiredIntParams("a")
 b <- RequiredIntParams("b")
} yield (a, b)

val (a, b): (List[Int], List[Int]) = reader(request)
// a = List(1, 2, 3)
// b = List(4, 5)
```

### HTTP Headers

The HTTP headers may also be read with `RequestReader`. The following pre-defined readers should be used:
* `io.finch.request.RequiredHeader` - fetches header or throws a `HeaderNotFound(header)` exception
* `io.finch.request.OptionalHeader` - fetches header into an `Option`

### HTTP Bodies

An HTTP body may be fetched from the HTTP request using the following readers:
* `io.finch.request.RequiredBody` - fetches the HTTP body as `Array[Byte]` of throws a `BodyNotFound` exception
* `io.finch.request.OptionalBody` - fetches the HTTP body as `Option[Array[Byte]]`
* `io.finch.request.RequiredStringBody` - fetches the HTTP body as `String` of throws a `BodyNotFound` exception
* `io.finch.request.OptionalStringBody` - fetches the HTTP body as `Option[String]`
* `io.finch.request.RequiredJsonBody` - fetches the HTTP body as JSON type provided by implicit instance of `DecodeJson`
* `io.finch.request.OptionalJsonBody` - fetches the HTTP body as an `Option` of JSON type provided by implicit instance of `DecodeJson`

## Responses

### Response Builder

An entry point into the construction of HTTP responses in Finch is the `io.finch.response.ResponseBuilder` class.  
It supports building of three types of responses:
* `application/json` within JSON object in the response body
* `plain/text` within string in the response body
* empty response of given HTTP status

The common practice is to not use the `ResponseBuilder` class directly but use the predefined response builders like
`Ok`, `SeeOther`, `NotFound` and so on.

```scala
import io.finch.json._
import io.finch.response._

val a = Ok() // an empty response with status 200
val b = NotFound("body") // 'plain/text' response with status 404
val c = Created(Json.obj("id" -> 42)) // 'application/json' response with status 201
```

HTTP headers may be added to respond instance with `withHeaders()` method:

```scala
val seeOther: ResponseBuilder = SeeOther.withHeaders("Some-Header-A" -> "a", "Some-Header-B" -> "b")
val rep: HttpResponse = seeOther(Json.obj("a" -> 10))
```

You might be surprised but Finch has response builders for _all_ the HTTP statuses: just import
`io.finch.response._` and start typing.

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

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is 100% compatible with
Finch.

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

Finch provides simple traits `io.finch.json.DecodeJson` and `io.finch.json.EncodeJson` to support pluggable JSON
libraries. An example of implementation those traits might be found in default JSON backend `finch-json`. All the methods
in Finch API that deals with JSON objects takes `implicit` decode or encode trait. So, the encode/decode implementations  
should be `implicit` in order to make its usage transparent. For example, the `finch-json` library might be plugged it by
the following imports:

```scala
import io.finch.json._        // imports immutable Json API
import io.finch.json.finch._  // imports implicit DecodeFinchJson and EncodeFinchJson
```

The naming convention for JSON libraries is the `io.finch.json.library-name._`.

There are bunch of API functions in Finch.io that implicitly take JSON encoder or decoder:
* `io.finch.response.ResponseBuilder#apply[A](json: A)(implicit encode: EncodeJson[A])`
* `io.finch.request.RequiredJsonBody#apply[A](implicit decode: DecodeJson[A])`
* `io.finch.request.OptionalJsonBody#apply[A](implicit decode: DecodeJson[A])`
* `io.finch.json.TurnJsonIntoHttp#apply[A](implicit encode: EncodeJson[A])`

### Finch-JSON

The Finch library is shipped with an immutable JSON API: `finch-json`, an extremely lightweight and simple
implementation of JSON: [Json.scala][3].

### Argonaut

The `finch-argonaut` module provides the support for the [Argonaut][4] JSON library.

*Note*, there is name conflict between Finch and Argonaut. Both of the projects use class names `EncodeJson` and 
`DecodeJson`. To avoid the naming conflicts, instead of importing the whole package `io.finch.json._` import only 
`io.finch.json.TurnJsonIntoHttp`. 

### Jawn

The `finch-jawn` module provides the support for [Jawn][5].

To decode a string with Jawn, you need to import `io.finch.json.jawn._` and define any Jawn `Facade` as an `implicit val`.
Using either `RequiredJsonBody` or `OptionalJsonBody` will take care of the rest.

To encode a value from the Jawn AST (specifically a `JValue`), you need only import `io.finch.json.jawn._` and
the `Encoder` will be imported implicitly.

[1]: https://github.com/finagle/finch/blob/master/demo/src/main/scala/io/finch/demo/Main.scala
[2]: http://www.haskell.org/haskellwiki/All_About_Monads#The_Reader_monad
[3]: https://github.com/finagle/finch/blob/master/finch-json/src/main/scala/io/finch/json/Json.scala
[4]: http://argonaut.io
[5]: https://github.com/non/jawn

