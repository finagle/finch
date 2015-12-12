## Endpoints

* [Overview](endpoint.md#overview)
* [Built-in Endpoints](endpoint.md#built-in-endpoints)
  * [Matching Endpoint](endpoint.md#matching-endpoints)
  * [Extracting Endpoints](endpoint.md#extracting-endpoints)
* [Composing Endpoints](endpoint.md#composing-endpoints)
  * [Path-based composition](endpoint.md#path-based-composition)
  * [Coproduct Endpoints](endpoint.md#coproduct-endpoints)
  * [Endpoints and Request Readers](endpoint.md#endpoints-and-request-readers)
* [Mapping Endpoints](endpoint.md#mapping-endpoints)
* [Outputs](endpoint.md#outputs)
* [Error Handling](endpoint.md#error-handling)

--

### Overview

An `Endpoint[A]` type represents an HTTP endpoint that takes an HTTP request and returns a value of type
`Option[Future[Output[A]]]`. This might seem like a complex type, although it's pretty straightforward when viewed
separately.

- `scala.Option` represents a success/failure of the _match_. Basically, `None` means "route not found" and will be
  converted into a very basic 404 response.
- `com.twitter.util.Future` represents an _async computation_, which might fail. An unhandled exceptions from `Future`
  is converted into a very basic 500 response.
- `io.finch.Output[A]` represents an _output context_ (headers, HTTP status, contentType, etc), which is used when
  serializing the underlying value of the `A` into a HTTP response.

With that said, an `Endpoint` is just a function `Request => Option[Future[Output[A]]]`, with which end users shouldn't
deal directly. The only type that matters in this signature is `A`, a type of the value returned from the endpoint and
will be serialized into an HTTP response.

Finch encourages its users to write low coupled and reusable endpoints that easy reason about. One may say that a
single `Endpoint` represents a particular _microservice_, which is nothing more than just a simple function. In this
case, an HTTP server (a Finagle HTTP `Service` that might be served within Finagle ecosystem) is represented as a
composition of endpoints.

### Built-in Endpoints

There are plenty of predefined endpoints that match the request or extract some value from it. You can get of all them
by importing `io.finch._`.

Before looking at the variety of endpoints available in Finch it makes sense to understand how exactly an `Endpoint`
views an HTTP request to match it or extract a value from it. An endpoint-friendly request is represented as
`io.finch.Input`, which encodes the request path as sequences of path segments splitted by `"/"`. For example, the
`/foo/bar/baz` path corresponds to `Input(Seq("foo", "bar, "baz""))`.

Basic mechanic behind _matching_ is pretty simple: an endpoint, drops the _head_ of the `Input` on every successful
match and propagates it to the next endpoint in the chain (see [composing endpoints](endpoint.md#composing-endpoints)
on how to construct those chains).

#### Matching Endpoints

A _matching endpoints_ is that one that doesn't extract anything from the request by only _matches_ it. This type of
endpoints is represented as `Endpoint[HNil]`, where `HNil` is an empty heterogeneous list from [Shapeless][shapeless].

All the matching endpoints are available via implicit conversions from strings, integers and booleans.

```scala
val users: Endpoint[HNil] = "users" // matches the head of the input
```

Matching the HTTP methods is done in a bit different way. There are functions of type `Endpoint[A] => Endpoint[A]` that
take some `Endpoint` and wrap it with an anonymous `Endpoint` that also matches the HTTP method.

```scala
val users: Endpoint[HNil] = get("users")
```

Note that string  `"users"` in the example above, is implicitly converted into a `Endpoint[HNil]`.

Finally, there two special endpoints `*` and `/`.

- The `*` endpoint always matches the input.
- The `/` endpoint represents an _identity_ endpoint that doesn't modify input.

```scala
val all: Endpoint[HNil] = get(/) // matches all the GET requests
```

#### Extracting Endpoints

Things are getting interesting with extracting endpoints. There are just five base extractors available for most of the
basic types.

- `string: Endpoint[String]`
- `long: Endpoint[Long]`
- `int: Endpoint[Int]`
- `boolean: Endpoint[Boolean]`
- `uuid: Endpoint[java.lang.UUID]`

Each extracting endpoint has a corresponding _tail extracting_ endpoints.

There are also tail extracting endpoints available out of the box. For example, the `strings` endpoint has type
`Endpoint[Seq[String]]` and extracts the rest of the path in the input.

By default, extractors named be their types, i.e., `"string"`, `"boolean"`, etc. But you can specify the custom name for
the extractor by calling the `apply` method on it. In the example below, the string representation of the endpoint `b`
is `":flag"`.

```scala
val b: Endpoint[Boolean] = boolean("flag")
```

### Composing Endpoints

It's time to catch the beauty of endpoint combinators API by composing the complex endpoints out of the simple
endpoints we've seen before. There are just three operators you will need to deal with:

- `/` that sequentially composes two endpoints into a `Endpoint[L <: HList]` (see [Shapeless' HList][hlist])
- `:+:` (a space invader operator) that composes two endpoints of different types in terms of boolean `or` into a
  `Endpoint[C <: Coproduct]` (see [Shapeless' Coproduct][coproduct])
- `?` that composes endpoints with [request readers](request.md)

#### Path-based Composition

Here is an example of an endpoint that matches a request `GET /users/:id/orders/:id` and extracts two integer values
`userId` and `ticketId` from its path.

```scala
import shapeless._

val e: Endpoint[Int :: Int :: HNil] =
   get("users" / int("userId") / "tickets" / int("ticketId"))
```

No matter what are the types of left-hand/right-hand endpoints (`HList`-based endpoint or value endpoint) when applied
to `/` compositor, the correctly constructed `HList` will be yielded as a result.

#### Coproduct Endpoints

A coproduct `Endpoint[A :+: B :+: CNil]` represents an endpoint that returns a value of either type `A` or type `B`. The
space invader compositor's mechanic is close to `orElse` function defined of `Option` and `Try`: if the first endpoint
fails to match the input, it fails through to the second one.

```scala
import shapeless._

case class Foo(i: Int)
case class Bar(s: String)

val fooBar: Endpoint[Foo :+: Bar :+: CNil] =
  Endpoint(Ok(Foo(42))) :+: Endpoint(Ok(Bar("bar")))
```

Any coproduct endpoint may be converted into a Finagle HTTP service (i.e., `Service[Request, Response]`) under the
certain circumstances: every type in a coproduct should has a corresponding implicit instance of `EncodeResponse` in the
scope.

#### Endpoints and Request Readers

It's possible to compose `Endpoints`s with `RequestReader`s. Such composition is done by the `?` method that takes
a `Endpoint[A]` and a `RequestReader[B]` and returns a `Endpoint[A :: B :: HNil]`.

```scala
import shapeless._

val r: RequestReader[Int :: String :: HNil] = param("a").as[Int] :: param("b")
val e1: Endpoint[Boolean] = Endpoint(OK(true))
val e2: Endpoint[Boolean :: Int :: String :: HNil] = e1 ? r
```

### Mapping Endpoints

A business logic in Finch is represented as an endpoint _transformation_ in a form of either `A => Future[Output[B]]` or
`A => Output[B]`. An endpoint is enriched with lightweight syntax allowing to use same method for both transformations:
the `Endpoint.apply` method takes care about applying the given function to the underlying `HList` with appropriate
arity as well as wrapping the right hand side `Output[B]` into a `Future`.

In the following example, an `Endpoint[Int :: Int :: HNil]` is mapped to a function `(Int, Int) => Output[Int]`.

```scala
val sum: Endpoint[Int] = post("sum" / int / int) { (a: Int, b: Int) =>
  Ok(a + b)
}
```

There is a special case when `Endpoint[L <: HList]` is converted into an endpoint of case class. For this purpose, the
`Endpoint.as[A]` method might be used.

```scala
case class Foo(i: Int, s: String)
val foo: Endpoint[Foo] = (int / string).as[Foo]
```

### Outputs

Every returned value from `Endpoint` is wrapped with `Output` that defines a context used while a value is serialized
into an HTTP response. There are two cases of `Output`: `Output.Payload` representing an actual value and
`Output.Failure` representing a user-defined failure occurred in the endpoint. By default, all the 2xx responses are
defined as payloads while the rest of outputs as failures.

`Output.Payload` carries an actual _value_ that will be serialized into a response body, while `Output.Failure` carries
an `Exception` cased this failure. A simplified version of this ADT is shown below.

```scala
sealed trait Output[A]
case class Payload[A](value: A) extends Output[A]
case class Failure(cause: Exception) extends Output[Nothing]
```

Having an `Output` defined as an ADT allows to return both payloads and failures from the same endpoint depending on the
condition result.

```scala
val divOrFail: Endpoint[Int] = post("div" / a / b) { (a: Int, b: Int) =>
  if (b == 0) BadRequest(new ArithmeticException("Can not divide by 0"))
  else Ok(a / b)
}
```

Payloads and failures are symmetric in terms of serializing `Output` into an HTTP response. With that said, in order to
convert an `Endpoint` into a Finagle service, there is should be an implicit instance of `EncodeResponse[Exception]`
available in the scope. For example, it might be defined in terms of Circe's `Encoder`:

```scala
implicit val encodeException: Encoder[Exception] = Encoder.instance(e =>
  Json.obj("message" -> Json.string(e.getMessage)))
```

By default, all the exception are converted into `plain/text` HTTP response containing the exception message in their
bodies.

While this approach works perfectly well with JSON libraries empowering type-classes as decoders/encoders, it doesn't
really fit well with libraries using runtime-reflection. Thus, when it comes to exception encoding, it involves some
workaround to enable [Jackson](json.md#jackson) support in Finch. See [eval][eval] for an idiomatic example.

### Error Handling

By analogy with `com.twitter.util.Future` API it's possible to _handle_ the failed future in the endpoint using the
similarly named methods:

- `Endpoint[A].handle[B >: A](Throwable => Output[B]): Endpoint[B]`
- `Endpoint[A].rescue[B >: A](Throwable => Future[Output[B]]): Endpoint[B]`

The following example handles the `ArithmeticException` propagated from `a / b`.

```scala
val divOrFail: Endpoint[Int] = post("div" / a / b) { (a: Int, b: Int) =>
  Ok(a / b)
} handle {
  case e: ArithmeticExceptions => BadRequest(e)
}
```

All the unhandled exceptions are converted into very basic 500 responses that don't carry any payload. Only Finch's
errors (i.e., `io.finch.Error`) are treated in a special way and converted into 400 responses with their messages
serialized according to the rules defined in the `EncodeResponse[Exception]` instance.

--
Read Next: [RequestReaders](request.md)

[shapeless]: https://github.com/milessabin/shapeless
[hlist]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#heterogenous-lists
[coproduct]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#coproducts-and-discriminated-unions
[eval]: https://github.com/finagle/finch/tree/master/examples/src/main/scala/io/finch/eval
