# Finch Documentation

* [Demo](docs.md#demo)
* [Endpoints](docs.md#endpoints)
* [Requests](docs.md#requests)
  * [Custom Request Types](docs.md#custom-request-types)
  * [Request Reader](docs.md#request-reader-monad)
  * [Query String Params](docs.md#query-string-params)
  * [Improved Error Handling with Applicative Syntax](docs.md#improved-error-handling-with-applicative-syntax)
  * [Validation](docs.md#validation)
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

There is a single-file `demo` project , which is a complete REST API backend written with `finch-core` and `finch-json` 
modules. The source code of the demo project is altered with useful comments that explain how to use its building blocks 
such as `Endpoint`, `RequestReader`, `ResponseBuilder`, etc. The `demo` module is just a single Scala file [Main.scala][1] 
that is worth reading.

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
to `Json` (see section [Json](docs.md#finch-json)). Since, all the endpoints have the same type (i.e.,
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

An `Endpoint[Req, Rep]` may be implicitly converted into `Service[Req, Rep]` by importing the `io.finch._` object. Thus,  
the following code is correct.

```scala
val e: Endpoint[MyRequest, HttpResponse] = ???
// an endpoint `e` will be converted to service implicitly
Httpx.serve(new InetSocketAddress(8081), e)
```

## Requests

### Custom Request Types

An `Endpoint` doesn't have any constraints on its type parameters. In fact any `Req` and `Rep` types may be used in 
`Endpoint` with just one requirement: there is should be an implicit view `Req => HttpRequest` available in the scope.  
This approach allows to define custom request type using composition but not inheritance. More precisely, the user-defined 
request `MyReq` may be smoothly integrated into the Finch stack just by its implicit view to an `HttpRequest`.
 
```scala
case class MyRequest(http: HttpRequest)
implicit val myReqEv = (req: MyRequest) => req.http
val e: Endpoint[MyReq, HttpResponse]
val req: MyReq = ???
val s = RequiredParam("foo")(req)
```

In the example above, the `MyRequest` type may be used in both `Endpoint` and `RequestReader` without any exceptions, 
since there is an implicit view `myReqEv` defined. See [demo][1] for the complete example of custom request types.

### Request Reader Monad

Finch has built-in request reader that implements the [Reader Monad][2] functional design pattern:
* `io.finch.request.RequestReader` reads `Future[A]`

The simplified signature of the `RequestReader` abstraction is similar to `Service` but with monadic API methods `map`
and `flatMap`:

```scala
trait RequestReader[A] {
  def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[A]
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


### Improved Error Handling with Applicative Syntax

In addition to the monadic style shown in the examples above, Finch also supports an applicative style.
The previous example for a request reader for a User can be rewritten in applicative style as follows:

```scala
case class User(name: String, age: Int, city: String)

val user: RequestReader[User] = 
  (RequiredParam("name") ~
  RequiredIntParam("age") ~
  OptionalParam("city")) map {
    case name ~ age ~ city => 
      User(name, age, city.getOrElse("Novosibirsk"))
  }
```

The main advantage of this style is that errors will be collected.
If the name parameter is missing and the age parameter cannot be converted
to an integer, both errors will be included in the failure of the Future 
(in an exception class `RequestReaderErrors` that has an `errors` property
of type `Seq[Throwable]`).

The monadic style on the other hand is fail-fast and will always only present
the first error it ran into. For this reason the applicative style is the
recommended syntax for all use cases where detailed error reporting
is essential. The monadic style is useful for the rare cases where one
reader depends on the result of another reader or for those who have a 
strong preference for the monadic style and do not care for sophisticated
error reporting.


### Validation

A reader allows for its result to be validated based on a predicate or a comination of predicates.
This can happen inline or based on predefined validation rules, and is possible with both
the monadic and the applicative syntax. The predefined rules can also be combined with `and`
or `or`.

```scala
case class User(name: String, age: Int)

// monadic syntax
val adult: RequestReader[User] = for {
  name <- RequiredParam("name")
  age <- RequiredIntParam("age").should("be greater than 18"){ _ > 18 }
} yield User(name, age)

// applicative syntax  
val adult2: RequestReader[User] = 
  (RequiredParam("name") ~
  RequiredIntParam("age").should("be greater than 18"){ _ > 18 }) map {
    case name ~ age => User(name, age)
}
  
// reusable validators
val beNonNegative = ValidationRule[Int]("be non-negative") { _ >= 0 }
def beSmallerThan(value: Int) = ValidationRule[Int](s"be smaller than $value") { _ < value }
  
val child: RequestReader[User] = 
  (RequiredParam("name") ~
  RequiredIntParam("age").should(beNonNegative and beSmallerThan(18))) map {
    case name ~ age => User(name, age)
}
```

The exceptions from a request-reader might be handled just like other future exceptions in Finagle:
```scala
val user: Future[Json] = service(...) handle {
  case ParamNotFound(param) => Json.obj("error" -> "param_not_found", "param" -> param)
  case ValidationFailed(param, rule) => Json.obj("error" -> "validation_failed", "param" -> param, "rule" -> rule)
}
```

Note, that all the exception throw by `RequestReader` are case classes. So, the pattern matching may be used to handle them.


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

* `io.finch.request.RequiredArrayBody` - fetches the HTTP body as `Array[Byte]` or throws a `BodyNotFound` exception
* `io.finch.request.OptionalArrayBody` - fetches the HTTP body as `Option[Array[Byte]]`
* `io.finch.request.RequiredStringBody` - fetches the HTTP body as `String` or throws a `BodyNotFound` exception
* `io.finch.request.OptionalStringBody` - fetches the HTTP body as `Option[String]`

Finch supports pluggable request decoders. In fact, any type `A` my be read from the request body using either:
`io.finch.request.RequiredBody[A]` or `io.finch.request.OptionalBody[A]` if there is an implicit value of type 
`DecodeRequest[A]` available in the scope. The `DecodeRequest[A]` abstraction may be described as function 
`String => Try[A]`. Thus, any decoders may be easily defined to use the functionality of body readers. Note, that 
the body type (i.e., `Double`) should be always explicitly defined for both `RequiredBody` and `OptionalBody`.

```
implicit val decodeDouble = new DecodeRequest[Double] {
  
  def apply(s: String): Try[Double] = Try { s.toDouble }
  
}
val req: HttpRequest = ???
val readDouble: RequestReader[Double] = RequiredBody[Double]
```


### Type Conversion

A `DecodeRequest[A]` can also be applied in a generic way to any `RequestReader[String]`, 
`RequestReader[Option[String]]` or `RequestReader[Seq[String]]`
as long as a matching implicit is in scope, through calling `as[A]` on the reader.

This is an example for applying an integer conversion to a reader:

```scala
implicit val decodeInt = new DecodeRequest[Int] {
   def apply(req: String): Try[Int] = Try(req.toInt)
}

val reader: RequestReader[Int] = RequiredParam("foo").as[Int]
```

The above is equivalent to the built-in `RequiredIntParam("foo")`, but the 
generic `as[A]` method allows to use `DecodeRequest` instances for any target
type, such as a Joda `DateTime` for example, making the `DecodeRequest` API
more broadly useful than just offering this functionality for the request body
as in previous Finch versions.


## Responses

### Response Builder

An entry point into the construction of HTTP responses in Finch is the `io.finch.response.ResponseBuilder` class.  
It supports building of three types of responses:
* `plain/text` within string in the response body
* empty response of given HTTP status
* a type defined by implicit instance of `EncodeResponse[A]`

The common practice is to not use the `ResponseBuilder` class directly but use the predefined response builders like
`Ok`, `SeeOther`, `NotFound` and so on.

```scala
import io.finch.json._
import io.finch.response._

val a = Ok() // an empty response with status 200
val b = NotFound("body") // 'plain/text' response with status 404
val c = Created(Json.obj("id" -> 42)) // 'application/json' response with status 201
```

`ResponseBuilder` may encode any type `A` into the HTTP response body if there is an implicit instance of `EncodeResponse[A]` 
available in the scope. An `EncodeResponse[A]` abstraction may be treated as a function `A => String` that also defines
a `content-type` value. For example, the implementation of `EncodeResponse[A]` for the `finch-json` module looks pretty
straightforward.

```scala
implicit val encodeFinchJson = new EncodeResponse[Json] {
  def apply(json: Json): String = Json.encode(json)
  def contentType: String = "application/json"
}
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

### Finch-JSON

The Finch library is shipped with an immutable JSON API: `finch-json`, an extremely lightweight and simple
implementation of JSON: [Json.scala][3]. The usage looks as follows:

```scala
import io.finch.json._
import io.finch.request._

vaj j: Json = Json.obj("a" -> 10)
val i: RequestReader[Json] = RequiredBody[Json]
val o: HttpResponse = Ok(Json.arr("a", "b", "c")

```

### Argonaut

The `finch-argonaut` module provides the support for the [Argonaut][4] JSON library.

### Jawn

The `finch-jawn` module provides the support for [Jawn][5].

To decode a string with Jawn, you need to import `io.finch.jawn._` and define any Jawn `Facade` as an `implicit val`.
Using either `RequiredJsonBody` or `OptionalJsonBody` will take care of the rest.

To encode a value from the Jawn AST (specifically a `JValue`), you need only import `io.finch.jawn._` and
the `Encoder` will be imported implicitly.

### Jackson

The `finch-jackson` module provides the support for the [Jackson][6] library. The library usage looks as follows:

```scala
import io.finch.jackson._
implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
case class Foo(id: Int, s: String)

val ok: HttpResponse = Ok(Foo(10, "foo")) // will be encoded as JSON
val foo: RequestReader[Foo] = RequiredBody[Foo] // a request reader that reads Foo
```


[1]: https://github.com/finagle/finch/blob/master/demo/src/main/scala/io/finch/demo/Main.scala
[2]: http://www.haskell.org/haskellwiki/All_About_Monads#The_Reader_monad
[3]: https://github.com/finagle/finch/blob/master/finch-json/src/main/scala/io/finch/json/Json.scala
[4]: http://argonaut.io
[5]: https://github.com/non/jawn
[6]: http://jackson.codehaus.org/

