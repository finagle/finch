# Finch Documentation

* [Demo](docs.md#demo)
* [Endpoints](docs.md#endpoints)
* [Requests](docs.md#requests)
  * [Custom Request Types](docs.md#custom-request-types)
  * [Request Reader](docs.md#request-reader) 
    * [Overview](docs.md#overview)
    * [API](docs.md#api)
  * [Base Readers](docs.md#base-readers)
    * [Required and Optional Readers](docs.md#required-and-optional-readers)
    * [Multi-Value Parameters](docs.md#multi-value-parameters)
    * [Custom Readers](docs.md#custom-readers)
    * [Error Handling](docs.md#error-handling)
  * [Combining and Reusing Readers](docs.md#combining-and-reusing-readers)
    * [Applicative Syntax](docs.md#applicative-syntax)
    * [Monadic Syntax](docs.md#monadic-syntax)
  * [Type Conversion](docs.md#type-conversion)
    * [Built-in Decoders](docs.md#built-in-decoders)
    * [Custom Decoders](docs.md#custom-decoders)
    * [Integration with JSON Libraries](docs.md#integration-with-json-libraries)
  * [Validation](docs.md#validation)
    * [Inline Validation](docs.md#inline-validation)
    * [Reusable Rules](docs.md#reusable-validators)
    * [Built-in Rules](docs.md#built-in-rules)
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
`Endpoint` with just one requirement: there should be an implicit view `Req => HttpRequest` available in the scope.  
This approach allows to define custom request types using composition but not inheritance. More precisely, the user-defined 
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


### Request Reader

Finch has a built-in request reader that implements the [Reader Monad][2] functional design pattern:
* `io.finch.request.RequestReader` reads `Future[A]`

Since the request readers read futures they might be chained together with regular Finagle services in a single
for-comprehension. Thus, reading the request params is an additional monad-transformation in the program's data flow.
This is extremely useful when a service should fetch and validate the request params before doing the real job and not
do the job at all if the params are not valid. Request reader might throw a future exception and none of further
transformations will be performed. Reader Monad is sort of a famous abstraction that is heavily used in Finch.


#### Overview

A typical `RequestReader` might look like this:

```scala
import io.finch.request._

case class User(name: String, age: Int, city: String)
    
val user: RequestReader[User] = (
  RequiredParam("name") ~
  RequiredParam("age").as[Int].shouldNot(beLessThan(18)) ~
  OptionalParam("city")
) map {
  case name ~ age ~ city => 
    User(name, age, city.getOrElse("Novosibirsk"))
}
```

A `RequestReader` is responsible for the following typical tasks of request processing:

* It reads parameters, header, cookies or the body of the request (see [Base Readers](docs.md#base-readers)).
* It declares these artifacts as either required or optional (see [Required and Optional Readers](docs.md#required-and-optional-readers)).
* It converts string-based input to other types with the `as[A]` method (see [Type Conversion](docs.md#type-conversion)).
* It validates one or more readers with `should` or `shouldNot` (see [Validation](docs.md#validation)).
* It combines multiple readers with the `~` combinator method (see [Combining and Reusing Readers](docs.md#combining-and-reusing-readers)).


#### API

The `RequestReader` API is fairly simple. It allows to apply the reader to a request instance with `apply`,
to transform the reader with `map`, `flatMap` and `embedFlatMap`, to combine it with other readers with
the `~` combinator and to validate it with `should` or `shouldNot`:

```scala
trait RequestReader[A] {
  def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[A]
  
  def map[B](fn: A => B): RequestReader[B]
  def flatMap[B](fn: A => RequestReader[B]): RequestReader[B]
  def embedFlatMap[B](fn: A => Future[B]): RequestReader[B]
  
  def ~[B](that: RequestReader[B]): RequestReader[A ~ B]
  
  def should(rule: String)(predicate: A => Boolean): RequestReader[A]
  def shouldNot(rule: String)(predicate: A => Boolean): RequestReader[A]
  def should(rule: ValidationRule[A]): RequestReader[A]
  def shouldNot(rule: ValidationRule[A]): RequestReader[A]
}
```

In addition there are implicit `as[A]` methods available for type conversion on string-based readers:

```scala
// for all `RequestReader[String]`
def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[A]

// for all `RequestReader[Option[String]]`
def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Option[A]]

// for all `RequestReader[Seq[String]]`
def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Seq[A]]
```

Don't think too much about these type signatures, it is all explained in 
[Type Conversion](docs.md#type-conversion).

The following sections cover all these features in more detail. All sample code assumes
that you have imported `io.finch.request._`.


### Base Readers

Finch provides a set of base readers for extracting parameters, headers, cookies or the body from 
the request. The column for the result type specifies the type parameter of the resulting reader
(e.g. `Option[String]` means the reader is a `RequestReader[Option[String]]`).

Request Item   | Reader Type                                     | Result Type  
-------------- | ----------------------------------------------- | ------------ 
Parameter      | `RequiredParam(name)`/`OptionalParam(name)`   | `String`/`Option[String]`
[Multi-Value Parameters](docs.md#multi-value-parameters) | `RequiredParams(name)`/`OptionalParams(name)` | `Seq[String]`/`Seq[String]`
Header         | `RequiredHeader(name)`/`OptionalHeader(name)` | `String`/`Option[String]`
Cookie         | `RequiredCookie(name)`/`OptionalCookie(name)` | `Cookie`/`Option[Cookie]`
Text Body      | `RequiredBody`/`OptionalBody`                 | `String`/`Option[String]`
Binary Body    | `RequiredBinaryBody`/`OptionalBinaryBody`     | `Array[Byte]`/`Option[Array[Byte]]`


#### Required and Optional Readers

As you can see in the table above, the 6 base readers all come in two flavors, allowing to
declare a request item as either required or optional.

* A `RequiredXxx` reader fails with a `NotPresent` exception if the item is not found in the request
* An `OptionalXxx` reader succeeds, producing a `None` if the item is not found in the request
* If you apply type conversions or validations to an optional item, the behaviour is as follows:
  * If the result is `None`, all type conversions and validations are skipped and the reader succeeds with a `None` result
  * If the result is non-empty, all type conversions and validations have to succeed or otherwise the reader will fail


#### Multi-Value Parameters

The `RequiredParams` and `OptionalParams` readers read multi-value parameters in the following way:

* In case of multiple occurrences of the same parameter in the URL, the values are combined into a single `Seq[String]`
* If any of the values is a comma-separated list, it will be split into `Seq[String]`

Thus, the following HTTP params `a=1,2,3&b=4&b=5` might be fetched with the `RequiredParams` reader like this:

```scala
val reader: RequestReader[(Seq[Int], Seq[Int])] = (
  RequiredParams("a").as[Int] ~
  RequiredParams("b").as[Int]
) map {
  case a ~ b => (a, b)
}

val (a, b): (Seq[Int], Seq[Int]) = reader(request)
// a = Seq(1, 2, 3)
// b = Seq(4, 5)
```


#### Custom Readers

In most cases you will combine several of the built-in base readers to compose new readers.
For the rare cases where you want to create a new reader type yourself, the `RequestReader`
companion object comes with a range of convenient factory methods:

```scala
// Creates a new reader that always succeeds, producing the specified value.
def value[A](value: A, item: RequestItem = MultipleItems): RequestReader[A]
    
// Creates a new reader that always fails, producing the specified exception.
def exception[A](exc: Throwable, item: RequestItem = MultipleItems): RequestReader[A]

// Creates a new reader that always produces the specified value.
def const[A](value: Future[A], item: RequestItem = MultipleItems): RequestReader[A]

// Creates a new reader that reads the result from the request.
def apply[A](item: RequestItem)(f: HttpRequest => A): RequestReader[A]
```

The `RequestItem` passed to all these factory methods gets used in the exceptions
a reader may throw and is an ADT consisting of the types `ParamItem`, `HeaderItem`,
`CookieItem`, `BodyItem` or `MultipleItems`. In most factory methods this parameter
is optional, so you can leave it out if your reader does not deal with one particular
request item.


#### Error Handling

The exceptions from a request reader might be handled just like other future exceptions in Finagle:
```scala
val user: Future[Json] = service(...) handle {
  case NotFound(ParamItem(param)) => 
    Json.obj("error" -> "param_not_found", "param" -> param)
  case NotValid(ParamItem(param), rule) => 
    Json.obj("error" -> "validation_failed", "param" -> param, "rule" -> rule)
}
```

All the exceptions throw by `RequestReader` are case classes. Therefore pattern matching may be used to handle them.

These are all error types produced by Finch (which all extend `RequestError`):

```scala
 // when multiple request items were invalid or missing
case class RequestErrors(errors: Seq[Throwable])

// when a required request item (header, param, cookie, body) was missing
case class NotFound(item: RequestItem)

// when type conversion failed
case class NotParsed(item: RequestItem, targetType: ClassTag[_], cause: Throwable) 

// when a validation rule did not pass for a request item
case class NotValid(item: RequestItem, rule: String) 
```


### Combining and Reusing Readers

As you have already seen in previous example, Finch provides the basic building
blocks for request processing in the form of readers for parameters, headers,
cookies and the request body.

You then perform type conversions or validations on these readers as required
and combine them to build new readers:

```scala
case class Address(street: String, city: String, postCode: String)

val address: RequestReader[Address] = (
  RequiredParam("street") ~
  RequiredParam("city") ~
  RequiredParam("postCode").shouldNot(beLongerThan(5))
) map {
  case street ~ city ~ postCode => 
    Address(street, city, postCode)
}
```

These new readers can then themselves be combined with other readers:
 
```scala
case class User(name: String, address: Address)

val user: RequestReader[User] = (
  RequiredParam("name") ~ address
) map {
  case name ~ address => 
    User(name, address)
}
```
  
The following sections will explain the difference between the applicative
style based on the `~` combinator you see in the examples above and the 
monadic style that you will only need in exceptional cases.


#### Applicative Syntax

Almost all the examples in this documentation show the applicative syntax based
on the `~` combinator for composing readers. It is roughly equivalent to the 
Applicative Builder `|@|` in scalaz, with a few subtle differences.

```scala
case class User(name: String, age: Int)
    
val user: RequestReader[User] = (
  RequiredParam("name") ~
  RequiredParam("age").as[Int]
) map {
  case name ~ age => User(name, age)
}
```

The main advantage of this style is that errors will be collected.
If the name parameter is missing and the age parameter cannot be converted
to an integer, both errors will be included in the failure of the Future, 
in an exception class `RequestErrors` that has an `errors` property
of type `Seq[Throwable]`:

```scala
user(Request("age" -> "broken"))

// will return a `Future` failing with this exception:
RequestErrors(Seq(
  NotPresent(ParamItem("name")),
  NotParsed(ParamItem("age"), <ClassTag[Int]>, <NumberFormatException>)
))
```


#### Monadic Syntax

Since the `RequestReader` is a Reader Monad you can alternatively
combine readers in for-comprehensions:

```scala
case class User(name: String, age: Int)
    
val user: RequestReader[User] = for {
  name <- RequiredParam("name")
  age <- RequiredParam("age").as[Int]
} yield User(name, age)
```

But while this syntax may look familiar and intuitive, it has the major
disadvantage that it is fail-fast. If both parameters are invalid, 
only one error will be returned. A fact your users and client developers 
probably won't fancy much.

The monadic style might still be useful for the rare cases where one
reader depends on the result of another reader.

Note: If you've used older versions of Finch (before 0.5.0), then 
the monadic style was the only way to combine readers. The applicative
style has been introduced in version 0.5.0 and is the recommended
combinator pattern now.


### Type Conversion

For all string based readers, Finch provides an `as[A]` method to perform 
type conversions. It is available for any `RequestReader[String]`, 
`RequestReader[Option[String]]` or `RequestReader[Seq[String]]`
as long as a matching implicit `DecodeRequest[A]` is in scope. 

This facility is designed to be intuitive, meaning that you do not
have to provide a `DecodeRequest[Seq[MyType]]` for converting a sequence.
A decoder for a single item will allow you to convert `Option[String]`
and `Seq[String]`, too:

```scala
RequiredParam("foo").as[Int]  // RequestReader[Int]
OptionalParam("foo").as[Int]  // RequestReader[Option[Int]]
RequiredParams("foo").as[Int] // RequestReader[Seq[Int]]
```

Note that the method signatures for `as[A]` show `DecodeMagnet[A]` as the required implicit,
but you can ignore this indirection as an implementation detail. All you ever have
to deal with yourself is bringing existing implicits for `DecodeRequest[A]` into scope
or implement such a decoder yourself.


#### Built-in Decoders

Finch comes with predefined decoders for `Int`, `Long`, `Float`, `Double` and `Boolean`.
As long as you have imported `io.finch.request._` the implicits for these decoders
are in scope and can be used with the `as[A]` method:

```scala
val reader: RequestReader[Int] = RequiredParam("foo").as[Int]
```


#### Custom Decoders

Writing a new decoder for a type not supported out of the box is very easy, too.
The following example shows a decoder for a Joda `DateTime` from a `Long` representing
the milliseconds since the epoch:

```scala
implicit val dateTimeDecoder: DecodeRequest[DateTime] = 
  DecodeRequest(s => Try(new DateTime(s.toLong)))
```

The example shows the most concise way of creating a new decoder: using the factory method
on the companion object of `DecodeRequest`:

```scala
def apply[A](f: String => Try[A]): DecodeRequest[A]
```

All you need to implement is a simple function from `String` to `Try[A]`.

As long as the implicit declared above is in scope you can then use your custom
decoder the same way as one of the built-in decoders (in this case for creating
a JodaTime `Interval`:

```scala
val user: RequestReader[Interval] = (
  RequiredParam("start").as[DateTime] ~
  RequiredParam("end").as[DateTime]
) map {
  case start ~ end => new Interval(start, end)
}
```


#### Integration with JSON Libraries

A third way of using the `as[A]` type conversion facility is to use one of the JSON
library integrations Finch offers. Finch comes with support for [Argonaut](docs.md#argonaut),
[Jawn](docs.md#jawn), [Jackson](jackson.md#jawn) and its own JSON support [Finch Json](docs.md#finch-json).

All these integration modules do is making the library-specific JSON decoders available for use
as a `DecodeRequest[A]`. To take Argonaut as an example, you only have to import `io.finch.argonaut._`
and then have the implicit Argonaut `DecodeJSON` instances in scope:

```scala
case class Person(name: String, age: Int)
 
implicit def PersonDecodeJson: DecodeJson[Person] =
  jdecode2L(Person.apply)("name", "age")
```

Finch will automatically adapt these implicits to its own `DecodeRequest[Person]` type, 
so that you can use the `as[A]` method on a reader for a body sent in JSON format:

```scala
val person: RequestReader[Person] = RequiredBody.as[Person]
```

The integration for the other JSON libraries works in a similar way.


### Validation

The `should` and `shouldNot` methods on `RequestReader` allow to perform validation logic.
If the specified predicate does not hold the reader will fail with a `NotValid(item, rule)`
exception. The `rule` is a description that you pass to the `should` or `shouldNot` methods
as a string.

Note that for an optional reader, the validation will be skipped for `None` results,
but if the value is non-empty then all validation must succeed for the reader to succeed. 

Validation can happen inline or based on predefined validation rules, as shown in the next
two sections.

  
#### Inline Validation

For validation logic only needed in one place, the most convenient way is to declare
it inline:

```scala
val adult2: RequestReader[User] = 
  (RequiredParam("name") ~
  RequiredParam("age").as[Int].shouldNot("be less than 18"){ _ < 18 }) map {
    case name ~ age => User(name, age)
}
```


#### Reusable Rules

If you perform the same validation logic in multiple readers, it is more convenient
to declare them separately and reuse them wherever needed:

```scala
val bePositive = ValidationRule[Int]("be positive") { _ > 0 }
def beLessThan(value: Int) = ValidationRule[Int](s"be less than $value") { _ < value }
  
val child: RequestReader[User] = 
  (RequiredParam("name") ~
  RequiredParam("age").as[Int].should(bePositive and beLessThan(18))) map {
    case name ~ age => User(name, age)
}
```

As you can see in the example above, predefined rules can also be logically combined
with `and` or `or`.


#### Built-in Rules

Finch comes with a small set of predefined rules. For readers producing numeric results
you can use `beLessThan(n: Int)` or `beGreaterThan(n: Int)`, for strings you can use
`beLongerThan(n: Int)` or `beShorterThan(n: Int)`.


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
val i: RequestReader[Json] = RequiredBody.as[Json]
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
val foo: RequestReader[Foo] = RequiredBody.as[Foo] // a request reader that reads Foo
```


[1]: https://github.com/finagle/finch/blob/master/demo/src/main/scala/io/finch/demo/Main.scala
[2]: http://www.haskell.org/haskellwiki/All_About_Monads#The_Reader_monad
[3]: https://github.com/finagle/finch/blob/master/finch-json/src/main/scala/io/finch/json/Json.scala
[4]: http://argonaut.io
[5]: https://github.com/non/jawn
[6]: http://jackson.codehaus.org/

