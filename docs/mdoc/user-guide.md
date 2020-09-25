---
layout: docs
title: User Guide
position: 1
---

## User Guide
* [Overview](#overview)
* [Understanding Endpoints](#understanding-endpoints)
* [Endpoint Instances](#endpoint-instances)
* [Composing Endpoints](#composing-endpoints)
  * [Product Endpoints](#product-endpoints)
  * [Coproduct Endpoints](#coproduct-endpoints)
* [Mapping Endpoints](#mapping-endpoints)
* [Outputs](#outputs)
* [Type-level Content-Type](#type-level-content-type)
* [Decoding](#decoding)
  * [Type Conversion](#type-conversion)
  * [Custom Decoders](#custom-decoders)
  * [Decoding from JSON](#decoding-from-json)
  * [Content-Type-based decoding](#content-type-based-decoding)
* [Encoding](#encoding)
  * [Encoding to JSON](#encoding-to-json)
* [JSON](#json)
  * [Circe](#circe)
  * [Argonaut](#argonaut)
  * [Jackson](#jackson)
  * [Json4s](#json4s)
  * [PlayJson](#playjson)
* [Validation](#validation)
* [Errors](#errors)
  * [Error Accumulation](#error-accumulation)
  * [Error Handling](#error-handling)
* [Streaming](#streaming)
* [Testing](#testing)
* [Telemetry](#telemetry)
* [Deriving Finagle Services](#deriving-finagle-services)


### Overview

An `Endpoint[A]` represents an HTTP endpoint that takes an HTTP request and returns a value of
type `A`. From the perspective of the category theory, this is an _applicative_ that embeds _state_,
which means two endpoints `Endpoint[A]` and `Endpoint[B]` might be composed/merged into an
`Endpoint[C]` when it's known how to compose/merge `(A, B)` into `C`.

Endpoints are composed in two ways: in terms of _and then_ and in terms of _or else_ combinators.

At the end of the day, an `Endpoint[A]` might be converted into a Finagle HTTP service so it might
be served within the Finagle ecosystem.

### Understanding Endpoints

Internally, an `Endpoint[A]` is represented as a function `Input => EndpointResult[A]`, where

- `Input` is a data type wrapping Finagle HTTP request with some Finch-specific context
- `EndpointResult[A]` is an ADT with two cases indicating if an endpoint was matched on a given
   input or not

Technically, `EndpointResult[A]` acts similarly to `Option[(Input, Output[A])]` implying that if
an endpoint is matched, both (Scala's `Tuple2`) the input remainder and the output are returned.

At this point, it's important to understand the endpoint lifecycle:

- Each incoming request is wrapped with `Input` and is passed to an endpoint
  (i.e., `Endpoint.apply(input)` - endpoint runs on a given input)
- A returned `EndpointResult` is (pattern-)matched against two cases:
  - When `Skipped` HTTP 404 is served back to the client
  - When `Matched` its output is _evaluated_ and the produced value or effect is served back to the
    client

Everything from above is happening automatically when endpoint is served as a Finagle service so as
a user you should neither deal with `Input` nor `EndpointResult` directly. Although, these types come
in handy when testing endpoints: it's quite easy to run an endpoint with an arbitrary `Input` and
then query its `EndpointResult` to assert the output. This testing business is covered in depth in
the [Testing](#testing) section. Although, some of the testing bits will be used later
in this user guide.

### Endpoint Instances

Finch comes with a number of built-in, simple endpoints representing well-defined operations that
you might want to perform on a given HTTP request.

#### Empty

`Endpoint.empty[A]` is the one that never matches.

#### Identity

An _identity_ endpoint `/` always matches but doesn't modify the state of the given input.

#### Constant

It might come in handy to _lift_ an arbitrary function or a value into an `Endpoint` context. Use
`Endpoint.const` to wrap an arbitrary value (evaluated eagerly) or any of the `Endpoint.liftX`
variants to lift a given call-by-name value (essentially, a function call) within an `Endpoint`.

In the following example, the random value is only generated once (when endpoint is constructed) in
the `p` endpoint, and generated on each request in the `q` endpoint.

```scala mdoc
import cats.effect.IO
import io.finch._
import io.finch.catsEffect._

// implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)

val p = Endpoint.const[IO, Double](scala.math.random)

val q = Endpoint.lift[IO, Double](scala.math.random)

p(Input.get("/")).awaitValueUnsafe()

p(Input.get("/")).awaitValueUnsafe()

q(Input.get("/")).awaitValueUnsafe()

q(Input.get("/")).awaitValueUnsafe()
```

#### Root (Request)

It's possible that Finch might be missing some of handy endpoints out of the box, especially that
it's evolved separately from Finagle. To overcome this and provide an extension point, there is a
special endpoint instance, called `root` that returns a raw Finagle `Request`.

```scala mdoc
import io.finch._, java.net.InetAddress

val remoteAddr = root.map(_.remoteAddress)

remoteAddr(Input.get("/")).awaitValueUnsafe()
```

#### Match All

A `*` endpoint always matches the entire path (all the segments).

#### Match Path

There is an implicit conversion from `String`, `Boolean` and `Int` to a matching endpoint that
matches the current path segment of a given request against a converted value.

```scala mdoc
import io.finch._, shapeless.HNil

val e: Endpoint[IO, HNil] = path("foo")

e(Input.get("/foo")).isMatched

e(Input.get("/bar")).isMatched
```

#### Extract Path

There are built-in matching endpoints that also extract a matched path segment as a value of a
requested type:

- `path[String]: Endpoint[String]`
- `path[Long]: Endpoint[Long]`
- `path[Int]: Endpoint[Int]`
- `path[Boolean]: Endpoint[Boolean]`
- `path[UUID]: Endpoint[java.lang.UUID]`

Each extracting endpoint has a corresponding _tail extracting_ endpoints.

There are also tail extracting endpoints available out of the box. For example, the `strings`
endpoint has type `Endpoint[Seq[String]]` and extracts the rest of the path in the input.

By default, extractors are named after their types, i.e., `"path[String]"`, `"path[Boolean]"`, etc.
But you can specify the custom name for the extractor by calling the `withToString` method on it.
In the example below, the string representation of the endpoint `b` is `":flag"`.

```scala mdoc
import io.finch._

path[Boolean].withToString("flag")
```

#### Match Verb

For every HTTP verb, there is a function `Endpoint[A] => Endpoint[A]` that takes a given endpoint of
an arbitrary type and enriches it with an additional check/match of the HTTP method/verb.

```scala mdoc:nest

import io.finch._, io.finch.catsEffect._

val e = path("foo")

val a = get(e)

a(Input.get("/foo")).isMatched

a(Input.post("/foo")).isMatched
```

#### Params

Finch aggregates for you all the possible param sources (query-string params from GET requests and
urlencoded params from POST requests) behind a single namespace `param*`. That being said, an
endpoint `param("foo")` works as follows: 1) tries to fetch param `foo` from the query string 2) if
the previous step failed, tries to fetch param `foo` from the urlencoded body.

Finch provides the following instances for reading HTTP params (evaluating endpoints):

- `param("foo")` - required param "foo"
- `paramOption("foo")` - optional param "foo"
- `params("foos")` - multi-value param "foos" that might return an empty sequence
- `paramsNel("foos")` - multi-value param "foos" that return `cats.data.NonEmptyList` or a failed
  `Future`

#### Headers

Instances for reading HTTP headers include both evaluating and matching instances.

- `header("foo")` - required header "foo"
- `headerOption("foo")` - optional header "foo"

#### Bodies

All the instances for reading HTTP bodies are evaluating endpoints that also involve matching in
some way: before evaluating an HTTP body they also check/match whether the request is
chunked/non-chunked. This is mostly about what API Finagle provides for streaming: chunked requests
may read via `request.reader`, non-chunked via `request.content`.

Similar to the rest of predefined endpoints, these come in pairs required/optional.

Non-chunked bodies:

- `stringBody(Option)` - required/optional, non-chunked (only matches non-chunked requests) body
   represented as a UTF-8 string.
- `binaryBody(Option)` - required/optional, non-chunked (only matches non-chunked requests) body
   represented as a byte array.

There is a special (and presumably most used) combinators available for _reading and decoding_ HTTP
bodies in a single step.

- `body(Option)[A, ContentType <: String]` - required/optional, non-chunked (only matches
  non-chunked requests) body represented as `A` and decoding according to presented
  `Decode.Aux[A, ContentType]` instance. See [decoding from JSON](#decoding-from-json)
  for more details.
- `jsonBody(Option)[A]` - an alias for `body[A, Application.Json]`.
- `textBody(Option)[A]` - an alias for `body[A, Text.Plain]`

Chunked bodies:

- `asyncBody` - chunked/streamed (only matches chunked requests) body represented as an
  `AsyncStream[Buf]`.

#### Multipart

Finch supports reading file uploads and attributes from the `multipart/form-data` HTTP bodies with
the help of four instances (evaluating endpoints that also only match non-chunked requests).

- `multipartFileUpload("foo")` - required, non-chunked file upload with name "foo"
- `multipartFileUploadOption("foo")` - optional, non-chunked file upload with name "foo"
- `multipartFileUploads("foo")` - non-chunked multiple file uploads with name "foo" (could be empty)
- `multipartFileUploadsNel("foo")` - required at least one, non-chunked multiple file upload with
  name "foo"
- `multipartAttribute("foo")` - required multipart attribute with name "foo"
- `multipartAttributeOption("foo")` - optional multipart attribute with name "foo
- `multipartAttributes("foo")` - multiple multipart attributes named "foo" (could be empty)
- `multipartAttributesNel("foo")` - multiple multipart attributes named "foo" (can't be empty)

#### Cookies

There are also two instances (evaluating endpoints) for reading cookies from HTTP requests/headers.

- `cookie("foo")` - required cookie with name "foo"
- `cookieOption("foo")` - optional cookie with name "foo"

### Composing Endpoints

It's time to see the beauty of the endpoint combinators API in action by composing the complex
endpoints out of the simple endpoints we've seen before. There are just two operators you will
need to deal with:

- `::` that composes two endpoints in terms of the _and then_ combinator into a product endpoint
  `Endpoint[L <: HList]` (see [Shapeless' HList][hlist])
- `:+:` that composes two endpoints of different types in terms of the _or else_ combinator into a
  coproduct endpoint `Endpoint[C <: Coproduct]` (see [Shapeless' Coproduct][coproduct])

As you may have noticed, Finch heavily uses [Shapeless][shapeless] to empower its composability in a
type-safe, boilerplate-less way.

#### Product Endpoints

A product endpoint returns a product type represented as an `HList`. For example, a product endpoint
`Endpoint[Foo :: Bar :: HNil]` returns two values of types `Foo` and `Bar` wrapped with `HList`. To
build a product endpoint, use the `::` combinator.

```scala mdoc
import io.finch._, shapeless._

val both = path[Int] :: path[String]
```

No matter what the types of left-hand/right-hand endpoints are (`HList`-based endpoint or value
endpoint), when applied to the `::` combinator, the correctly constructed `HList` will be yielded.

#### Coproduct Endpoints

A coproduct `Endpoint[A :+: B :+: CNil]` represents an endpoint that returns a value of either type
`A` or type `B`. The `:+:` (i.e., space invader) combinator  mechanic is close to the `orElse`
function defined in `Option` and `Try`: if the first endpoint fails to match the input, it fails
through to the second one.

```scala mdoc
import io.finch._, shapeless._

val either = path[Int] :+: path[String]
```

Any coproduct endpoint may be converted into a Finagle HTTP service (i.e.,
`Service[Request, Response]`) under certain circumstances: every type in a coproduct should have
a corresponding implicit instance of `EncodeResponse` in the scope.

### Mapping Endpoints

A business logic in Finch is represented as an endpoint _transformation_ in a form of either
`A => Future[Output[B]]` or `A => Output[B]`. An endpoint is enriched with lightweight syntax
allowing us to use the same method for both transformations: the `Endpoint.apply` method takes
care of applying the given function to the underlying `HList` with appropriate arity as well as
wrapping the right hand side `Output[B]` into a `Future`.

In the following example, an `Endpoint[Int :: Int :: HNil]` is mapped to a function
`(Int, Int) => Output[Int]`.

```scala mdoc:nest
import io.finch._, shapeless._

val both = path[Int] :: path[Int]

val sum = both.mapOutput { case a :: b :: HNil => Ok(a + b) }
```

There is a special case when `Endpoint[L <: HList]` is converted into an endpoint of case class. For
this purpose, the `Endpoint.as[A]` method might be used.

```scala mdoc
import io.finch._, shapeless._

case class Bar(i: Int, s: String)

val bar = (path[Int] :: path[String]).as[Bar]
```

It's also possible to be explicit and use one of the `map*` methods defined on `Endpoint[A]`:

- `map[B](fn: A => B): Endpoint[B]`
- `mapAsync[B](fn: A => Future[B]): Endpoint[B]`
- `mapOutput[B](fn: A => Output[B]): Endpoint[B]`
- `mapOutputAsync[B](fn: A => Future[Output[B]]): Endpoint[B]`

### Outputs

Every returned value from `Endpoint` is wrapped with `Output` that defines a context used while a
value is serialized into an HTTP response. There are three cases of `Output`:

- `Output.Payload` representing an actual value returned as a payload
- `Output.Failure` representing a user-defined failure occurred in the endpoint
- `Output.Empty` representing an empty (without any payload) response

A simplified version of this ADT is shown below.

```scala mdoc
sealed trait Output[A]
object Output {
  case class Payload[A](value: A) extends Output[A]
  case class Failure(cause: Exception) extends Output[Nothing]
  case object Empty extends Output[Nothing]
}
```

Having an `Output` defined as an ADT allows us to return both payloads and failures from the same
endpoint depending on the conditional result.

```scala mdoc
import io.finch._, io.finch.catsEffect._

val divOrFail: Endpoint[IO, Int] = post("div" :: path[Int] :: path[Int]) { (a: Int, b: Int) =>
  if (b == 0) BadRequest(new ArithmeticException("Can not divide by 0"))
  else Ok(a / b)
}
```

Payloads and failures are symmetric in terms of serializing `Output` into an HTTP response. In
order to convert an `Endpoint` into a Finagle service, there should be an implicit instance of
`Encode[Exception]` for a given content-type available in the scope. For example, it might be defined
in terms of Circe's `Encoder`:

```scala mdoc
import io.finch._, io.circe._

implicit val encodeException: Encoder[Exception] = Encoder.instance(e =>
  Json.obj("message" -> Json.fromString(e.getMessage)))
```

NOTE: This instance is already available whenever `io.finch.circe._` import is present (similar for
any other of JSON library supported).

### Type-level Content-Type

Finch brings HTTP `Content-Type` to the type-level as a singleton string (i.e., `CT <: String`) to
make it affect implicit resolution and make sure that the right encoder/decoder will be picked by a
compiler. This is done lift the following kind of errors at compile time:

 - a `Text.Plain` service won't compile when only Circe's JSON encoders are available in the scope
 - an `Application.Json` body endpoint won't compile when no JSON library support is imported

Given that `Content-Type` is a separate concept, which is neither attached to `Endpoint` nor `Output`,
the way to specify it is to explicitly pass a requested `Content-Type` either to a `toServiceAs`
method call (to affect encoding) or `body` endpoint creation (to affect decoding).

```scala mdoc
import com.twitter.finagle.http.{Request, Response}, com.twitter.finagle.Service, io.finch._

val e = get(root) { _: Request => Ok("Hello, World!") }
val s = e.toServiceAs[Text.Plain]
```

The program above will do the right thing (will pick the right decoder) even when JSON encoders are
imported into the scope.

By default, Finch defines type-aliases for `text/plain` and `application/json` encoders as
`Encode.Text[A]` and `Encode.Json[A]`. For everything else, `Encode.Aux[A, CT <: String]` should be
used instead.

### Decoding

While Finch takes care about extracting some particular parts of a request (i.e., body, params,
headers) in their origin form (usually as `String`s), it's user's responsibility to convert/decode
them into the domain types.

Most of the means for decoding in Finch are built around three simple type-classes used in different
scenarios:

 - `io.finch.DecodePath[A]` - decodes path segments represented as strings into `Option[A]`
 - `io.finch.DecodeEntity[A]` - decodes string-based entities (eg: params and headers) into `Try[A]`
 - `io.finch.Decode.Aux[A, ContentType <: String]` - decodes bodies represented as `Buf`s (in a
   given content type) into `Try[A]`

Separating those three completely different use cases not only allows to define a clear boundaries
where abstraction's concerns end, but also helps performance-wise quite a lot.

#### Type Conversion

For the vast majority of endpoints, Finch also accepts a target type as a type parameter such that
a corresponding implicit decoder (i.e., `io.finch.DecodeEntity[A]`) will be resolved and applied
automatically.

This facility is designed to be intuitive, meaning that you do not have to provide a
`io.finch.DecodeEntity[Seq[MyType]]` for converting a sequence. A decoder for a single item will
allow you to convert optinal endpoints too:

```scala mdoc
import io.finch._

param[Int]("foo")

paramOption[Int]("bar")

params[Int]("baz")
```

Note when no type parameter is specified, `String` is being resolved implicitly.

A similar machinery is also available on any `Endpoint[L <: HList]` via `.as[A]` method to perform
[Shapeless][shapeless]-powered generic conversions from `HList`s to case classes with appropriately
typed members.

```scala mdoc:nest
import io.finch._

case class Extract(i: Int, s: String)

val foo = (param[Int]("i") :: param[String]("s")).as[Extract]
```

#### Custom Decoders

Writing a new decoder for a type not supported out of the box is very easy, too. The following
example shows a decoder for a Joda `DateTime` from a `Long` representing the number of milliseconds
since the epoch:

```scala
import cats.implicits._
import io.finch._
import org.joda.time.DateTime

implicit val dateTimeDecoder: DecodeEntity[DateTime] =
  DecodeEntity.instance(s => Either.catchNonFatal(new DateTime(s.toLong)))
```

All you need to implement is a simple function from `String` to `Try[A]`.

As long as the implicit declared above is in scope, you can then use your custom decoder in the same
way as any of the built-in decoders (in this case for creating a JodaTime `Interval`):

```scala mdoc
import io.finch._

case class Interval(start: Long, end: Long)

val interval = (
  param[Long]("start") ::
  param[Long]("end")
).as[Interval]
```

#### Decoding from JSON

There are two API entry point into decoding JSON payloads: `jsonBody[A]` and `jsonBodyOption[A]`.
These require a `Decode.Json[A]` instance to be available in the scope whenever they called.

Finch comes with support for a number of [JSON libraries](#json). All these integration modules do
is make the library-specific JSON decoders available for use as a `io.finch.Decode.Json[A]`. To take
Circe as an example, you only have to import `io.finch.circe._` and have implicit `io.circe.Decoder[A]`
instances in scope:

```scala mdoc
import io.finch._
import io.finch.circe._
import io.circe.Decoder, io.circe.Encoder, io.circe.generic.semiauto._

case class Person(name: String, age: Int)

implicit val decoder: Decoder[Person] = deriveDecoder[Person]
implicit val encoder: Encoder[Person] = deriveEncoder[Person]

```

Finch will automatically adapt these implicits to its own `io.finch.Decode.Json[Person]` type,  so
that you can use the `jsonBody(Option)` endpoints to read the HTTP bodies sent in a JSON format:

```scala mdoc
import io.finch._, com.twitter.io.Buf

val p = jsonBody[Person]

p(Input.post("/").withBody[Application.Json](Buf.Utf8("""{"name":"foo","age":42}""")))
```

The integration for the other JSON libraries works in a similar way.

### Content-Type-based decoding

Finch supports multiple decoders in the single `body` endpoint selecting the decoder with respect to the `Content-Type`
header of a request. This behavior can be enabled using `shapeless.Coproduct`:

```scala mdoc:nest
import io.finch.internal._, shapeless._, com.twitter.io.Buf, cats.syntax.either._

//example decoder and encoder for text/plain content-type.
//Represents Person as a string with semicolon delimeter
implicit val decodeTextPlainPerson: Decode.Aux[Person, Text.Plain] = Decode.instance((b, cs) => {
  Either.catchNonFatal({
    val l = b.asString(cs).split(";").toList
    Person(l.head, l.last.toInt)
  })
})

implicit val encodeTextPlainPerson: Encode.Aux[Person, Text.Plain] = Encode.instance((a, cs) => {
 Buf.Utf8(s"${a.name};${a.age}")
})

val person = Person("John", 42)
val json = Input.post("/").withBody[Application.Json](person)
val plain = Input.post("/").withBody[Text.Plain](person)

//Use coproduct to define support for multiple Content-Types
val e = body[Person, Application.Json :+: Text.Plain :+: CNil]

e(json).awaitValue()
e(plain).awaitValue()
```

If there was no `Content-Type` header in a request or none of the decoders supports it,
last one in the coproduct is used.

### Encoding

Behind-the-scene encoding of values returned from endpoint was always the essential part of Finch's
design. This what makes it all about domain types, not HTTP primitives. By analogy with decoding,
encoding is built around `io.finch.Encode[A]` type-class that takes a value of an arbitrary type
and converts that into a binary buffer that can be served in the HTTP payload/body.

#### Encoding to JSON

Encoding to JSON is not different from encoding to `application/xml` or anything else besides having
`Encode.Json[A]` instances in the scope for each type returned from the endpoints.

Even though Finch is abstracted over the concrete `Content-Type` it's still biased towards JSON.
This is why the `toService` call defaults to JSON and UTF-8 considered the default charset.

### JSON

Finch uses type classes `io.finch.Encode` and `io.finch.Decode` to make its JSON support pluggable.
Thus in most of the cases it's not necessary to make any code changes (except for import statements)
while switching the underlying JSON library.

Finch comes with a rich support of many modern JSON libraries. While it's totally possible to use
Finch with runtime reflection based libraries such as [Jackson][jackson], it's highly recommended to
use compile-time based solutions such as [Circe][circe] and [Argonaut][argonaut] instead. When
starting out, Circe would be the best possible choice as a JSON library due to its great performance
and a lack of boilerplate.

Use the following instructions to enable support for a particular JSON library.

#### Circe

* Add the dependency to the `finch-circe` module.
* Make sure for each domain type that there are implicit instances of `io.circe.Encoder[A]` and
  `io.circe.Decoder[A]` in the scope or that Circe's generic auto derivation is used via
  `import io.circe.generic.auto_`.

```scala mdoc
import io.finch.circe._
import io.circe.generic.auto._
```

It's also possible to import the Circe configuration which uses a pretty printer configured with
`dropNullValues = true`. Use the following imports instead:

```scala mdoc
import io.finch.circe.dropNullValues._
import io.circe.generic.auto._
```

#### Argonaut

* Add the dependency to the `finch-argonaut` module.
* Make sure for each domain type there are instances of `argonaut.EncodeJson[A]` and
  `argonaut.DecodeJson[A]` in the scope.

```scala
import argonaut._
import argonaut.Argonaut._
import io.finch.argonaut._

implicit val e: EncodeJson[_] = ???
implicit val d: DecodeJson[_] = ???
```

In addition to the very basic Argonaut pretty printer (available via `import io.finch.argonaut._`),
there are three additional configurations available out of the box:

* `import io.finch.argonaut.dropNullKeys._` - brings both decoder and encoder (uses the pretty
  printer that drops null keys) in the scope
* `import io.finch.argonaut.preserveOrder._` - brings both decoder and encoder (uses the pretty
  printer that preserves fields order) in the scope
* `import io.finch.argonaut.preserveOrderAndDropNullKeys._` - brings both decoder and encoder (uses
  the pretty printer that preserves fields order as well as drops null keys) in the scope

#### Jackson

* Add the dependency to the `finch-jackson` module.
* Import `import io.finch.jackson._`

While finch-jackson seems like the easiest way to enable JSON support in Finch, it's probably the
most dangerous one due to the level of involvement of the runtime based reflection.

#### Json4s

* Add the dependency to the `finch-json4s` module.
* Make sure there is an implicit instance of `Formats` in the scope.

```scala
import io.finch.json4s._
import org.json4s.DefaultFormats

implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
```

#### PlayJson

* Add the dependency to the `finch-playjson` module.
* For any type you want to serialize or deserialize you are required to create the appropriate
  Play JSON `Reads` and `Writes`.

```scala
import io.finch.playjson._
import play.api.libs.json._

case class Foo(name: String, age: Int)

object Foo {
  implicit val fooReads: Reads[Foo] = Json.reads[Foo]
  implicit val fooWrites: Writes[Foo] = Json.writes[Foo]
}
```

#### Spray-Json

* Add the dependency to the `finch-sprayjson` module.
* Create an implicit format convertor value for any type you defined.

```scala
import io.finch.sprayjson._
import spray.json._
import Defaultjsonprotocol._

case class Foo(name: String, age: Int)

object Foo {
  //Note: `2` means Foo has two members;
  //       No need for apply if there is no companion object
  implicit val fooformat = jsonFormat2(Foo.apply)
}
```

### Validation

The `should` and `shouldNot` methods on `Endpoint` allow you to perform validation logic. If the
specified predicate does not hold, the reader will fail with a `io.finch.Error.NotValid` exception.

Note that for an optional reader, the validation will be skipped for `None` results, but if the
value is non-empty then all validations must succeed for the reader to succeed.

For validation logic only needed in one place, the most convenient way is to declare it inline:

```scala mdoc:silent
import cats.effect.IO
import io.finch._, io.finch.catsEffect._

case class User(name: String, age: Int)

val user: Endpoint[IO, User] = (
  param[String]("name") ::
  param[Int]("age").shouldNot("be less than 18") { _ < 18 }
).as[User]
```

If you perform the same validation logic in multiple endpoints, it is more convenient to declare
them separately and reuse them wherever needed:

```scala mdoc:silent
import io.finch._

val bePositive = ValidationRule[Int]("be positive") { _ > 0 }
def beLessThan(value: Int) = ValidationRule[Int](s"be less than $value") { _ < value }

val child: Endpoint[IO, User] = (
  param("name") ::
  param[Int]("age").should(bePositive and beLessThan(18))
).as[User]
```

As you can see in the example above, predefined rules can also be logically combined with `and` or
`or`.

Finch comes with a small set of predefined rules. For readers producing numeric results, you can use
`beLessThan(n: Int)` or `beGreaterThan(n: Int)`, and for strings you can use `beLongerThan(n: Int)`
or `beShorterThan(n: Int)`.

### Errors

An endpoint may fail (it may evaluate into a `Future.exception`) by a number of reasons: it was
transformed/mapped to one that fails; it's an evaluating endpoint that fails if the incoming request
doesn't satisfy some condition (e.g., should have a query string param `foo`).

Having said that, you might want to _handle_ exceptions from the endpoint (even a coproduct one) to
make sure a remote client will receive them in a serialized form. Otherwise they will be dropped -
converted into very basic 500 responses that don't carry any payload.

Finch itself throws three kinds of errors represented as either `io.finch.Error` (a single error) or
`io.finch.Errors` (multiple errors) that are already handled as 400s (bad requests):

- `io.finch.Error.NotPresent` - when a required request part/item (header, param, body, cookie) was
  missing
- `io.finch.Error.NotParsed` - when type conversion failed
- `io.finch.Error.NotValid` - when a validation rule defined on an endpoint did not pass

#### Error Accumulation

[Product endpoints](#product-endpoints) play critical role in error accumulation in
Finch. Essentially, a product of two endpoints accumulates Finch's own errors (i.e., `io.finch.Error`
indicating a parse/validation failure or a missing entity) into `io.finch.Error` and will fail-fast
with the first non-Finch error (just ordinary `Exception`) observed.

The reasoning behind this design decision is following. When an arbitrary failure (just `Exception`)
occurs in one of the parts of a product endpoint, it's not super clear that it's safe to keep
evaluating the next part since it's unknown if the failure was local to a given request and didn't
side-affect an entire process. Finch's own errors are known to be locally scoped hence safe to
accumulate.

#### Error Handling

By analogy with `com.twitter.util.Future` API it's possible to _handle_ the failed future in the
endpoint using the similarly named methods:

- `Endpoint[A].handle[B >: A](Throwable => Output[B]): Endpoint[B]`
- `Endpoint[A].rescue[B >: A](Throwable => Future[Output[B]]): Endpoint[B]`

The following example handles the `ArithmeticException` propagated from `a / b`.

```scala mdoc
import io.finch._
import io.finch.catsEffect._

val divOrFail = post("div" :: path[Int] :: path[Int]) { (a: Int, b: Int) =>
  Ok(a / b)
} handle {
  case e: Exception => BadRequest(e)
}
```

All the unhandled exceptions are converted into very basic 500 responses that don't carry any
payload. Only Finch's errors (i.e., `io.finch.Error`) are treated in a special way and converted
into 400 responses with their messages serialized according to the rules defined in the
`io.finch.Encode.Aux[Exception, ContentType]` instance.

Define your own instance if you want to serialize handled exception into a payload of given
content-type. For example, here is an instance for HTML.

```scala mdoc:silent:nest
import io.finch._, com.twitter.io.Buf

implicit val e: Encode.Aux[Exception, Text.Html] = Encode.instance((e, cs) =>
  Buf.Utf8(s"<h1>Bad thing happened: ${e.getMessage}<h1>")
)
```

**Finch used to provide exception encoders** from all of its json libraries, but due to some issues
with implicit scope that made defining custom encoders difficult, you must now **define your own**.
Here is an example Json encoder for finch-circe:

```scala mdoc:silent
import io.circe._, io.finch._

def encodeErrorList(es: List[Exception]): Json = {
  val messages = es.map(x => Json.fromString(x.getMessage))
  Json.obj("errors" -> Json.arr(messages: _*))
}

implicit val encodeException: Encoder[Exception] = Encoder.instance({
  case e: io.finch.Errors => encodeErrorList(e.errors.toList)
  case e: io.finch.Error =>
    e.getCause match {
      case e: io.circe.Errors => encodeErrorList(e.errors.toList)
      case err => Json.obj("message" -> Json.fromString(e.getMessage))
    }
  case e: Exception => Json.obj("message" -> Json.fromString(e.getMessage))
})
```
This encoder will handle any accumulated Finch and Circe errors in addition to single exceptions.

If no other `Encode[Exception]` is available, Finch provides a fallthrough of `Encode.Aux[Exception, ?]`
that will return an empty content body.

### Streaming

The `finch-iteratee` module enables high-level support of chunked request and response streaming using
[iteratee.io](https://github.com/travisbrown/iteratee) and `circe-iteratee` libraries. It allows encoding and decoding
newline delimited JSON streams, but also supports binary and text streaming.  
Concept of [iteratee](https://en.wikipedia.org/wiki/Iteratee) could be complicated for understanding, but
proves itself very powerful and useful abstraction over processing sequential data. The API of `iteratee.io` library 
is transparent and covers most of the potential use cases.

Main points:

- `Enumerator` is a "lazy storage" with all the chunks that already were received or about to be received in a future
- `Enumeratee` is a `map` part and could be used to transform chunks in `Enumerator`
- `Iteratee` is a `reduce` part that runs computation and processes over chunks in `Enumerator`

**Decoding**

Currently JSON decoding is supported only in `finch-circe` module and implemented
using `circe-iteratee` library.

Finch plugs in iteratee-powered decoding support via the `io.finch.iteratee.Enumerate` type-class:

```scala mdoc:silent
import java.nio.charset.Charset
import com.twitter.util.Future
import io.iteratee._

/**
  * Enumerate HTTP streamed payload represented as [[Enumerator]] (encoded with [[Charset]]) into
  * an [[Enumerator]] of arbitrary type `A`.
  */
trait Enumerate[A] {

  type ContentType <: String

  def apply(enumerator: Enumerator[Future, Buf], cs: Charset): Enumerator[Future, A]
}
```

Here you can see an example how to work with decoding enumerators:

```scala mdoc:nest
import io.iteratee.{Enumerator, Iteratee}
import io.circe.generic.auto._
import io.finch._, io.finch.circe._, io.finch.iteratee._

case class Element(bar: Int)

/**
  * Sum stream values together
  */
val decodingJSON = post("foo" :: jsonBodyStream[Enumerator, Element]) { (enumerator: Enumerator[IO, Element]) =>
  val enumeratorOfInts: Enumerator[IO, Int] = enumerator.through(Enumeratee.map[IO, Element, Int](_.bar))
  val futureSum: IO[Int] = enumeratorOfInts.into(Iteratee.fold[IO, Int, Int](0)(_ + _))
  futureSum.map(Ok) //future will be completed when whole stream is folded
}

// Some async task to store foos.
val storeFoos: Vector[Element] => IO[Unit] = _ => IO.pure(())

/**
  * Backpressure with HTTP 1.1 could be implemented only in a way of closing a connection.
  * If you don't want to consume more data, you could throw any exception while processing `Enumerator`,
  * it'll close a connection.
  *
  * In this example connection going to be closed as soon as 10 elements has been stored.
  */
val backpressureJSON = post("bar" :: jsonBodyStream[Enumerator, Element]) { (enumerator: Enumerator[IO, Element]) =>
  val iteratee = Iteratee.take[IO, Element](10).flatMapM { (fs: Vector[Element]) => 
    // Processing pipeline will be interrupted in the case of an exception
    storeFoos(fs).flatMap(_ => IO.raiseError(new InterruptedException))
  }
  enumerator.into(iteratee).map(Ok)
}
/**
  * You could use `enumeratorBody` if there is no need to decode input stream.
  * Have in mind that for something except `Buf` one should provide implicit instance 
  * of `io.finch.iteratee.Enumerate` in scope
  */
val bufEnumerator =
  post("text" :: binaryBodyStream[Enumerator]) { buf: Enumerator[IO, Array[Byte]] =>
    Ok(new Array[Byte](0))
  }
```

The `finch-refined` module provides support for [refined][refined] types in path segments, query parameters and
other request entities. This approach enables validation of API on type level:

```scala mdoc:nest
import java.net.URL

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._
import io.finch._, io.finch.refined._, io.finch.catsEffect._

val e = get("foo" :: param[Int Refined Positive]("int")) { (i: Int Refined Positive) =>
  Ok(i.value)
}

val u = get("foo" :: param[String Refined Url]("url")) { (s: String Refined Url) =>
  Ok(new URL(s.value))
}

e(Input.get("/foo?int=-1")).awaitValue()
```


**Encoding**

Beside decoding of input stream, it's possible to make output stream with enumerator serving
`Endpoint[Enumerator[Future, A]]`:

```scala mdoc:nest
import io.iteratee.Enumerator
import io.circe.generic.auto._
import io.finch._, io.finch.circe._, io.finch.iteratee._

case class Foo(x: Int)

val streamingEndpoint = get("stream") {
  Ok(Enumerator.enumList[IO, Foo](List(Foo(1), Foo(2))))
}
```

### Testing

One of the advantages of typeful endpoints in Finch is that they can be unit-tested independently in
a way similar to how functions are tested. The machinery is pretty straightforward: an endpoint
takes an `Input` and returns `EndpointResult` that could be queried with `await*()` methods.

**Building `Input`s**

There is a lightweight API around `Input`s to make them easy to build. For example, the following
builds a `GET /foo?a=1&b=2` request:

```scala mdoc
import io.finch._

val foo = Input.get("/foo", "a" -> "2", "b" -> "3")
```

Similarly a payload (`application/x-www-form-urlencoded` in this case) with headers may be added
to an input:

```scala mdoc
import io.finch._

val bar = Input.post("/bar").withForm("a" -> "1", "b" -> "2").withHeaders("X-Header" -> "Y")
```

Additionally, there is JSON-specific support in the `Input` API through `withBody`.

```scala mdoc
import io.circe.generic.auto._, io.finch._, io.finch.circe._

case class Baz(m: Map[String, String])

val baz = Input.put("/baz").withBody[Application.Json](Baz(Map("a" -> "b")))
```

Note that, assuming UTF-8 as the encoding, which is the default, `application/json;charset=utf-8`
will be added as content type.

**Querying `EndpointResult`s**

Similarly to the `Input` API for testing, `EndpointResult` comes with a number of blocking methods
(prefixed with `await`) designed to be used in tests.

```scala mdoc
import io.finch._, com.twitter.finagle.http.Status

val divOrFail = post(path[Int] :: path[Int]) { (a: Int, b: Int) =>
  if (b == 0) BadRequest(new Exception("div by 0"))
  else Ok(a / b)
}

divOrFail(Input.post("/20/10")).awaitValueUnsafe() == Some(2)

divOrFail(Input.get("/20/10")).awaitValueUnsafe() == None

divOrFail(Input.post("/20/0")).awaitOutputUnsafe().map(_.status) == Some(Status.BadRequest)
```

You can find unit tests for the examples in the [examples folder][examples].

### Telemetry

When it comes to observing certain metrics within endpoints (usually, a derived Finagle `Service`),
it becomes channeling to distinguish between individual endpoints in a coproduct. To solve this and
provide users with a means to report per-endpoint telemetry, when matched, endpoints return an
instance of `io.finch.Trace` object that represents a matched path.

```scala mdoc:nest
import io.finch._, io.finch.catsEffect._

val foo = get("foo" :: "bar" :: path[String]) { s: String => Ok(s) }

val bar = get("bar" :: "foo" :: path[Int]) { i: Int => Ok(i) }

val fooBar = foo :+: bar

fooBar(Input.get("/foo/bar/baz")).trace

fooBar(Input.get("/bar/foo/10")).trace
```

A `Trace` instance returned from an endpoint (including coproducts) can be captured on the call-site
(presumably, in a Finagle filter) using Twitter Future Locals.

```scala mdoc:nest
import io.finch._, io.finch.catsEffect._, com.twitter.finagle.http.Request

val foo = get("foo" :: path[String]) { s: String => Ok(s) }

val s = foo.toServiceAs[Text.Plain]

Trace.capture { s(Request("/foo/bar")).map(_ => Trace.captured).poll }
```

A couple of things worth noting with regards to the "tracing" machinery:

 - There is no need to explicitly enable it in `Bootstrap` options, a `Trace` will always be
   captured within a `Trace.capture` context.

 - There is no need to wait for a service's future to resolve before retrieving a captured `Trace`
   as it's immediately available once endpoint is matched (i.e., after the `service.apply` call).

 - Obviously materializing a new structure on each request comes at the cost of allocations/running
   time. We, however, haven't observed any significant overhead in Finch's benchmarks.

### Deriving Finagle Services

Ultimately, any Finch program (i.e., endpoint(s)) should be translated into an HTTP service,
exposable to the outside world.

Finch uses compile-time machinery to derive Finagle HTTP services out of endpoints. `Bootstrap`
represents an entry point API into this derivation. In a nutshell, `Bootstrap` provides only single
method: `serve[A, CT <: String]` that takes an endpoint of given type `A` and serves it with a
content type `CT`.

Bootstrap can also be configured with a `configure` method. At this point, we only support two
options:

- `includeServerHeader` (enabled by default) and
- `includeDateHeader` (enabled by default)

The quick start example looks fairly straightforward:

```scala mdoc:nest
import io.finch._, io.finch.circe._
import io.circe.generic.auto._
import com.twitter.finagle.Http

val json = get("json") { Ok(Map("foo" -> "bar")) }

val text = get("text") { Ok("Hello, World!") }

val s = Bootstrap.configure(includeServerHeader = false).
  serve[Application.Json](json).
  serve[Text.Plain](text).
  toService
```

[shapeless]: https://github.com/milessabin/shapeless
[hlist]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#heterogenous-lists
[coproduct]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#coproducts-and-discriminated-unions
[examples]: https://github.com/finagle/finch/tree/master/examples/src/test/scala/io/finch
[argonaut]: http://argonaut.io
[jackson]: http://wiki.fasterxml.com/JacksonHome
[json4s]: http://json4s.org/
[circe]: https://github.com/circe/circe
[circe-jackson]: https://github.com/circe/circe-jackson
[circe-jackson-performance]: https://github.com/circe/circe-jackson#jackson-vs-jawn
[playjson]: https://www.playframework.com/documentation/2.6.x/ScalaJson
[refined]: https://github.com/fthomas/refined
[spray-json]: https://github.com/spray/spray-json
