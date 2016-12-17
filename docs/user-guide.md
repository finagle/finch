## Finch User Guide

* [Overview](user-guide.md#overview)
* [Endpoint Internals](user-guide.md#endpoint-internals)
* [Understanding Endpoints](user-guide.md#understanding-endpoints)
* [Endpoint Instances](user-guide.md#endpoint-instances)
* [Composing Endpoints](user-guide.md#composing-endpoints)
  * [Product Endpoints](user-guide.md#product-endpoints)
  * [Coproduct Endpoints](user-guide.md#coproduct-endpoints)
* [Mapping Endpoints](user-guide.md#mapping-endpoints)
* [Outputs](user-guide.md#outputs)
* [Type-level Content-Type](user-guide.md#type-level-content-type)
* [Decoding](user-guide.md#decoding)
  * [Type Conversion](user-guide.md#type-conversion)
  * [Custom Decoders](user-guide.md#custom-decoders)
  * [Decoding from JSON](user-guide.md#decoding-from-json)
* [Encoding](user-guide.md#encoding)
  * [Encoding to JSON](user-guide.md#encoding-to-json)
* [Validation](user-guide.md#validation)
* [Errors](user-guide.md#errors)
  * [Error Accumulation](user-guide.md#error-accumulation)
  * [Error Handling](user-guide.md#error-handling)
* [Testing](user-guide.md#testing)

--

### Overview

An `Endpoint[A]` represents an HTTP endpoint that takes an HTTP request and returns a value of
type `A`. From the perspective of the category theory, this is an _applicative_ that embeds _state_,
which means two endpoints `Endpoint[A]` and `Endpoint[B]` might be composed/merged into an
`Endpoint[C]` when it's known how to compose/merge `(A, B)` into `C`.

Endpoints are composed in two ways: in terms of _and then_ and in terms of _or else_ combinators.

At the end of the day, an `Endpoint[A]` might be converted into a Finagle HTTP service so it might
be served within the Finagle ecosystem.

### Endpoint Internals

Internally, an `Endpoint[A]` is represented as a function `Request => Option[Future[Output[A]]]`.
This might seem like a complex type, although it's pretty straightforward when viewed separately.

- `scala.Option` represents a success/failure of the _match_. Basically, `None` means "route not
  found" and will be converted into a very basic 404 response.
- `com.twitter.util.Future` represents an _async computation_, which might fail. An unhandled
  exception from `Future` is converted into a very basic 500 response.
- `io.finch.Output[A]` represents an _output context_ (headers, HTTP status, content type, etc),
  which is used when serializing the underlying value of the `A` into an HTTP response.

You shouldn't deal directly with that type, everything explained above happens internally.
The only type that matters in this signature is `A`, a type of the value returned from the endpoint
which will be serialized into an HTTP response.

Finch encourages you to write low coupled and reusable endpoints that are easy to reason about. One
may say that a single `Endpoint` represents a particular _microservice_, which is nothing more than
just a simple function. In this case, an HTTP server (a Finagle HTTP `Service` that might be served
within the Finagle ecosystem) is represented as a composition of endpoints.

### Understanding Endpoints

An `Endpoint[A]` represents a function that needs to be run/called to produce some value/effect.
This work is usually done by a service to which an endpoint is converted (wrapped with). So a
Finagle service wrapping an endpoint 1) takes an HTTP request 2) converts it into a format Finch can
understand 3) runs an endpoint 4) returns the value returned from the endpoint run/call.

Everything above seems pretty straightforward, except for what it really means to _run_ an
endpoint. Running an endpoint consists of two stages: _match_ and _evaluate_. When the request comes
in, it's matched against all the endpoints (composed in terms of _or else_) and then the one that
matched is evaluated.

An important thing to understand is that the _match_ stage (represented as an `Option`) never fails,
but the _evaluate_ stage (represented as `Future`) may fail (e.g., param is missing).

Keeping those two stages in mind, we can distinct two types of endpoint instances depending on when
request reading (for `Endpoint[A]`, fetching a value of type `A` out of a given request) is actually
happening: _matching_ and _evaluating_.

**Matching Endpoints** are strict (requests are read while matched); affect routing/matching; when
matched also extract some value out of the given request (i.e., path segment); when evaluated return
an extracted value.

**Evaluating Endpoints** are lazy (requests are read while evaluated); always match (don't affect
routing/matching); when evaluated return a value fetched from the given request.

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

```scala
scala> import io.finch._
import io.finch._

scala> val p = Endpoint.const(scala.math.random)
p: io.finch.Endpoint[Double] = io.finch.Endpoint$$anon$12@3d326f2c

scala> val q = Endpoint.lift(scala.math.random)
q: io.finch.Endpoint[Double] = io.finch.Endpoint$$anon$13@4126006a

scala> p(Input.get("/")).value
res1: Option[Double] = Some(0.46142040016536523)

scala> p(Input.get("/")).value
res2: Option[Double] = Some(0.46142040016536523)

scala> q(Input.get("/")).value
res3: Option[Double] = Some(0.2204303223747196)

scala> q(Input.get("/")).value
res4: Option[Double] = Some(0.38292485936910126)
```

#### Root (Request)

It's possible that Finch might be missing some of handy endpoints out of the box, especially that
it's evolved separately from Finagle. To overcome this and provide an extension point, there is a
special endpoint instance, called `root` that returns a raw Finagle `Request`.

```scala
scala> import io.finch._
import io.finch._

scala> val remoteAddr = root.map(_.remoteAddress)
remoteAddr: io.finch.Endpoint[java.net.InetAddress] = request

scala> remoteAddr(Input.get("/")).value
res5: Option[java.net.InetAddress] = Some(0.0.0.0/0.0.0.0)
```

#### Match All

A `*` endpoint always matches the entire path (all the segments).

#### Match Path

There is an implicit conversion from `String`, `Boolean` and `Int` to a matching endpoint that
matches the current path segment of a given request against a converted value.

```scala
scala> import io.finch._
import io.finch._

scala> val e: Endpoint0 = "foo"
e: io.finch.Endpoint0 = foo

scala> e(Input.get("/foo")).isDefined
res1: Boolean = true

scala> e(Input.get("/bar")).isDefined
res2: Boolean = false
```

#### Extract Path

There are built-in matching endpoints that also extract a matched path segment as a value of a
requested type:

- `string: Endpoint[String]`
- `long: Endpoint[Long]`
- `int: Endpoint[Int]`
- `boolean: Endpoint[Boolean]`
- `uuid: Endpoint[java.lang.UUID]`

Each extracting endpoint has a corresponding _tail extracting_ endpoints.

There are also tail extracting endpoints available out of the box. For example, the `strings`
endpoint has type `Endpoint[Seq[String]]` and extracts the rest of the path in the input.

By default, extractors are named after their types, i.e., `"string"`, `"boolean"`, etc. But you can
specify the custom name for the extractor by calling the `apply` method on it. In the example
below, the string representation of the endpoint `b` is `":flag"`.

```scala
scala> import io.finch._
import io.finch._

scala> boolean("flag")
res1: io.finch.Endpoint[Boolean] = :flag
```

#### Match Verb

For every HTTP verb, there is a function `Endpoint[A] => Endpoint[A]` that takes a given endpoint of
an arbitrary type and enriches it with an additional check/match of the HTTP method/verb.

```scala
scala> import io.finch._, com.twitter.finagle.http.{Request, Method}
import io.finch._
import com.twitter.finagle.http.{Request, Method}

scala> val e = get(/)
e: io.finch.Endpoint[shapeless.HNil] = GET /

scala> e(Input.post("/")).isDefined
res1: Boolean = false

scala> e(Input.get("/")).isDefined
res2: Boolean = true
```

#### Params

Finch aggregates for you all the possible param sources (query-string params, urlencoded params and
multipart params) behind a single namespace `param*`. That being said, an endpoint `param("foo")`
works as follows: 1) tries to fetch param `foo` from the query string 2) if the previous step
failed, tries to fetch param `foo` from the urlencoded body 3) if the previous step failed, tries
to fetch param `foo` from the multipart body.

Finch provides the following instances for reading HTTP params (evaluating endpoints):

- `param("foo")` - required param "foo"
- `paramOption("foo")` - optional param "foo"
- `params("foos")` - multi-value param "foos" that might return an empty sequence
- `paramsNel("foos")` - multi-value param "foos" that return `cats.data.NonEmptyList` or a failed
  `Future`

In addition to these evaluating endpoints, there is also one matching endpoint `paramExists("foo")`
that only matches requests with "foo" param.

#### Headers

Instances for reading HTTP headers include both evaluating and matching instances.

- `header("foo")` - required header "foo"
- `headerOption("foo")` - optional header "foo"
- `headerExists("foo")` - only matches requests that contain header "foo"

#### Bodies

All the instances for reading HTTP bodies are evaluating endpoints that also involve matching in
some way: before evaluating an HTTP body they also check/match whether the request is
chunked/non-chunked. This is mostly about what API Finagle provides for streaming: chunked requests
may read via `request.reader`, non-chunked via `request.content`.

Similar to the rest of predefined endpoints, these are come in pairs required/optional.

Non-chunked bodies:

- `bodyString(Option)` - required/optional, non-chunked (only matches non-chunked requests) body
   represented as a UTF-8 string.
- `binaryBody(Option)` - required/optional, non-chunked (only matches non-chunked requests) body
   represented as a byte array.

There is a special (and presumably most used) combinators available for _reading and decoding_ HTTP
bodies in a single step.

- `body(Option)[A, ContentType <: String]` - required/optional, non-chunked (only matches
  non-chunked requests) body represented as `A` and decoding according to presented
  `Decode.Aux[A, ContentType]` instance. See [decoding from JSON](user-guide.md#decoding-from-json)
  for more details.
- `jsonBody(Option)[A]` - an alias for `body[A, Application.Json]`.
- `textBody(Option)[A]` - an alias for `body[A, Text.Plain]`

Chunked bodies:

- `asyncBody` - chunked/streamed (only matches chunked requests) body represented as an
  `AsyncStream[Buf]`.

#### File Uploads

Finch supports reading file uploads from the `multipart/form-data` HTTP bodies with the help of two
instances (evaluating endpoints that also only match non-chunked requests).

- `fileUpload("foo")` - required, non-chunked (only matches non-chunked requests) file upload with
  name "foo"
- `fileUploadOption("foo")` - optional, non-chunked (only matches non-chunked requests) file upload
  with name "foo"

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

```scala
import io.finch._
import shapeless._

val i: Endpoint[Int] = ???
val s: Endpoint[String] = ???
val both: Endpoint[Int :: String :: HNil] = i :: s
```

No matter what the types of left-hand/right-hand endpoints are (`HList`-based endpoint or value
endpoint), when applied to the `::` compositor, the correctly constructed `HList` will be yielded.

#### Coproduct Endpoints

A coproduct `Endpoint[A :+: B :+: CNil]` represents an endpoint that returns a value of either type
`A` or type `B`. The `:+:` (i.e., space invader) combinator  mechanic is close to the `orElse`
function defined in `Option` and `Try`: if the first endpoint fails to match the input, it fails
through to the second one.

```scala
import io.finch._
import shapeless._

val i: Endpoint[Int] = ???
val s: Endpoint[String] = ???
val either: Endpoint[Int :+: String :+: CNil] = i :+: s
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

```scala
import io.finch._
import io.shapeless._

val both: Endpoint[Int :: Int :: HNil] = ???
val sum: Endpoint[Int] = both { (a: Int, b: Int) => Ok(a + b) }
```

There is a special case when `Endpoint[L <: HList]` is converted into an endpoint of case class. For
this purpose, the `Endpoint.as[A]` method might be used.

```scala
import io.finch._
import shapeless._

case class Foo(i: Int, s: String)
val is: Endpoint[Int :: String :: HNil] = ???

val foo: Endpoint[Foo] = is.as[Foo]
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

```scala
sealed trait Output[A]
object Output {
  case class Payload[A](value: A) extends Output[A]
  case class Failure(cause: Exception) extends Output[Nothing]
  case object Empty extends Output[Nothing]
}
```

Having an `Output` defined as an ADT allows us to return both payloads and failures from the same
endpoint depending on the conditional result.

```scala
import io.finch._

val divOrFail: Endpoint[Int] = post("div" :: int :: int) { (a: Int, b: Int) =>
  if (b == 0) BadRequest(new ArithmeticException("Can not divide by 0"))
  else Ok(a / b)
}
```

Payloads and failures are symmetric in terms of serializing `Output` into an HTTP response. In
order to convert an `Endpoint` into a Finagle service, there should be an implicit instance of
`Encode[Exception]` for a given content-type available in the scope. For example, it might be defined
in terms of Circe's `Encoder`:

```scala
implicit val encodeException: Encoder[Exception] = Encoder.instance(e =>
  Json.obj("message" -> Json.string(e.getMessage)))
```

NOTE: This instance is already available whenever `io.finch.circe._` import is present (simlar for
any other of JSON library supported).

### Type-level Content-Type

Finch brings HTTP `Content-Type` to the type-level as a singleton string (i.e., `CT <: String`) to
make it affect implicit resolution and make sure that the right encoder/decoder will be picked by a
compiler. This is done lift the following kind of errors at compile time:

 - a `Text.Plain` service won't compile when only Circe's JSON encoders are available in the scope
 - a `Application.Json` body endpoint won't compile when no JSON library support is imported

Given that `Content-Type` is a separate concept, which is neither attached to `Endpoint` nor `Output`,
the way to specify it is to explicitly pass a requested `Content-Type` either to a `toServiceAs`
method call (to affect encoding) or `body` endpoint creation (to affect decoding).

```scala
val e: Endpoint[String] = get(/) { Ok("Hello, World!") }
val s: Service[Request, Response] = e.toServiceAs[Text.Plain]
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

For all `String`-based endpoints, Finch provides an `as[A]` method to perform type conversions. It
is available for any `Endpoint[String]`, `Endpoint[Option[String]]` or `Endpoint[Seq[String]]` as
long as a matching implicit `io.finch.DecodeEntity[A]` type-class is in the scope.

This facility is designed to be intuitive, meaning that you do not have to provide a
`io.finch.DecodeEntity[Seq[MyType]]` for converting a sequence. A decoder for a single item will
allow you to convert `Option[String]` and `Seq[String]`, too:

```scala
scala> import io.finch._
import io.finch._

scala> param("foo").as[Int]
res1: io.finch.Endpoint[Int] = param(foo)

scala> paramOption("bar").as[Int]
res2: io.finch.Endpoint[Option[Int]] = param(bar)

scala> params("bazs").as[Int]
res3: io.finch.Endpoint[Seq[Int]] = param(bazs)
```

The same method `as[A]` is also available on any `Endpoint[L <: HList]` to perform
[Shapeless][shapeless]-powered generic conversions from `HList`s to case classes with appropriately
typed members.

```scala
scala> import io.finch._
import io.finch._

scala> case class Foo(i: Int, s: String)
defined class Foo

scala> val e = param("i").as[Int] :: param("s")
e: io.finch.Endpoint[shapeless.::[Int,shapeless.::[String,shapeless.HNil]]] = param(i) :: param(s)

scala> val foo = e.as[Foo]
foo: io.finch.Endpoint[Foo] = param(i) :: param(s)
```

Note that while both methods take different implicit params and use different techniques to perform
type-conversion, they're basically doing the same thing: transforming the underlying type `A` into
some type `B` (that's why they have similar names).

#### Custom Decoders

Writing a new decoder for a type not supported out of the box is very easy, too. The following
example shows a decoder for a Joda `DateTime` from a `Long` representing the number of milliseconds
since the epoch:

```scala
import io.finch._

implicit val dateTimeDecoder: DecodeEntity[DateTime] =
  Decode.instance(s => Try(new DateTime(s.toLong)))
```

All you need to implement is a simple function from `String` to `Try[A]`.

As long as the implicit declared above is in scope, you can then use your custom decoder in the same
way as any of the built-in decoders (in this case for creating a JodaTime `Interval`):

```scala
import io.finch._

val interval: Endpoint[Interval] = (
  param("start").as[DateTime] ::
  param("end").as[DateTime]
).as[Interval]
```

#### Decoding from JSON

There are two API entry point into decoding JSON payloads: `jsonBody[A]` and `jsonBodyOption[A]`.
These require a `Decode.Json[A]` instance to be available in the scope whenever they called.

Finch comes with support for a number of [JSON libraries](json.md). All these integration modules do
is make the library-specific JSON decoders available for use as a `io.finch.Decode.Json[A]`. To take
Circe as an example, you only have to import `io.finch.circe._` and have implicit `io.circe.Decoder[A]`
instances in scope:

```scala
scala> import io.circe.Decoder, io.circe.generic.semiauto

scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Person(name: String, age: Int)
object Person {
  implicit val decoder: Decoder[Person] = semiauto.deriveDecoder
}

// Exiting paste mode, now interpreting.

defined class Person
defined object Person
```

Finch will automatically adapt these implicits to its own `io.finch.Decode.Json[Person]` type,  so
that you can use the `jsonBody(Option)` endpoints to read the HTTP bodies sent in a JSON format:

```scala
scala> import io.finch._

scala> val p = jsonBody[Person]
p: io.finch.Endpoint[Person] = body

scala> p(Input.post("/").withBody[Application.Json](Buf.Utf8("""{"name":"foo","age":42}"""))).value
res2: Option[Person] = Some(Person(foo,42))
```

The integration for the other JSON libraries works in a similar way.

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

### Validation

The `should` and `shouldNot` methods on `Endpoint` allow you to perform validation logic. If the
specified predicate does not hold, the reader will fail with a `io.finch.Error.NotValid` exception.

Note that for an optional reader, the validation will be skipped for `None` results, but if the
value is non-empty then all validations must succeed for the reader to succeed.

For validation logic only needed in one place, the most convenient way is to declare it inline:

```scala
import io.finch._

case class User(name: String, age: Int)

val user: Endpoint[User] = (
  param("name") ::
  param("age").as[Int].shouldNot("be less than 18") { _ < 18 }
).as[User]
```

If you perform the same validation logic in multiple endpoints, it is more convenient to declare
them separately and reuse them wherever needed:

```scala
import io.finch._

val bePositive = ValidationRule[Int]("be positive") { _ > 0 }
def beLessThan(value: Int) = ValidationRule[Int](s"be less than $value") { _ < value }

val child: Endpoint[User] = (
  param("name") ::
  param("age").as[Int].should(bePositive and beLessThan(18))
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

- `io.finch.Error.NotFound` - when a required request part/item (header, param, body, cookie) was
  missing
- `io.finch.Error.NotParsed` - when type conversion failed
- `io.finch.Error.NotValid` - when a validation rule defined on an endpoint did not pass

#### Error Accumulation

[Product endpoints](user-guide.md#product-endpoints) play critical role in error accumulation in
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

```scala
val divOrFail: Endpoint[Int] = post("div" :: int :: int) { (a: Int, b: Int) =>
  Ok(a / b)
} handle {
  case e: ArithmeticExceptions => BadRequest(e)
}
```

All the unhandled exceptions are converted into very basic 500 responses that don't carry any
payload. Only Finch's errors (i.e., `io.finch.Error`) are treated in a special way and converted
into 400 responses with their messages serialized according to the rules defined in the
`io.finch.Encode.Aux[Exception, ContentType]` instance.

Finch provides some very basic instances of Encode[Exception] in the following cases:

 - `Encode.Json[Exception]` is available with any of the supported JSON libraries
   (i.e., with `import io.finch.$jsonLibrary)
 - `Encode.Text[Exception]` is available out of the box in finch-core
 - `Encode.Aux[Exception, ?]` that doesn't encode anything is available for any other
    content-type

Define your own instance if you want to serialize handled exception into a payload of given
content-type. For example, here is an instance for HTML.

```scala
import io.finch._
import io.finch.internal.BufText

implicit val e: Encode.Aux[Exception, Text.Html] = Encode.instance((e, cs) =>
  BufText(s"<h1>Bad thing happened: ${e.getMessage}<h1>", cs)
)
```

### Testing

One of the advantages of typeful endpoints in Finch is that they can be unit-tested independently in
a way similar to how functions are tested. The machinery is pretty straightforward: an endpoint
takes an `Input` and returns `Output` (wrapping a payload).

There is a lightweight API around `Input`s to make them easy to build. For example, the following
builds a `GET /foo?a=1&b=2` request:

```scala
import io.finch._

val foo: Input = Input.get("/foo", "a" -> "2", "b" -> "3")
```

Similarly a payload (`application/x-www-form-urlencoded` in this case) with headers may be added
to an input:

```scala
import io.finch._
val bar: Input = Input.post("/bar")
  .withForm("a" -> "1", "b" -> "2")
  .withHeaders("X-Header" -> "Y")
```

Additionally, there is JSON-specific support in the `Input` API through `withBody`.

```scala
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

case class Baz(m: Map[String, String])
val baz: Input = Input.put("/baz").withBody[Application.Json](Baz(Map("a" -> "b")))
```

Note that, assuming UTF-8 as the encoding, which is the default, `application/json;charset=utf-8`
will be added as content type.

Similarly when an `Output` is returned form the `Endpoint` it might be queried with a number of
methods: `tryValue`, `tryOutput`, `output`, and `value`. Please, note that all of those are blocking
on the underlying `Future` with the upper bound of 10 seconds.

In general, it's recommended to use `try*` variants (since they don't throw exceptions), but for the
sake of simplicity, in this user guide `value` and `output` are used instead.

```scala
scala> val divOrFail: Endpoint[Int] = post(int :: int) { (a: Int, b: Int) =>
     |   if (b == 0) BadRequest(new Exception("div by 0"))
     |   else Ok(a / b)
     | }
divOrFail: io.finch.Endpoint[Int] = POST /:int/:int

scala> divOrFail(Input.post("/20/10")).value == Some(2)
res3: Boolean = true

scala> divOrFail(Input.get("/20/10")).value == None
res4: Boolean = true

scala> divOrFail(Input.post("/20/0")).output.map(_.status) == Some(Status.BadRequest)
res6: Boolean = true
```

You can find unit tests for the examples in the [examples folder][examples].

--
Read Next: [Authentication](auth.md)

[shapeless]: https://github.com/milessabin/shapeless
[hlist]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#heterogenous-lists
[coproduct]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#coproducts-and-discriminated-unions
[examples]: https://github.com/finagle/finch/tree/master/examples/src/test/scala/io/finch
