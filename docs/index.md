<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/finch-logo.png" width="360px" />
</p>

The Finch library provides an immutable layer of functions and types atop of [Finagle][1] for writing lightweight HTTP
services. It roughly contains three packages: [io.finch.route](route.md), [io.finch.request](request.md),
[io.finch.response](response.md), which correspond to three simple steps to a robust REST/HTTP API:

#### Step 1: Routing the HTTP requests to a `Service`

The [Router](route.md#router) abstraction routes the requests depending on their path and method information. `Router`
combinator provides a bunch of predefined routers handling separated parts of a route. `Router`s might be composed with
either `/` (`flatMap`) or `/>` (`map`) operator. There is also `|` (`orElse`) operator that combines two routers in
terms of the inclusive or operator.

```scala
val router: Endpoint[HttpRequest, HttpResponse] = Get / ("users" | "user") / int /> GetUser
```

#### Step 2: Reading the HTTP requests in a `Service`

The [RequestReader](request.md#request-reader) abstraction is responsible for reading any details form the HTTP request.
`RequestReader` is composable in both ways: via the monadic API (using the for-comprehension, i.e., `flatMap`/`map`) and
via the applicative API (using the `~` operator). These approaches define an unlimited number of readers out the plenty
of predefined ones.

```scala
val pagination: RequestReader[(Int, Int)] =
  paramOption("offset").as[Int] ~ paramOption("limit").as[Int] map {
    case offset ~ limit => (offset.getOrElse(0), limit.getOrElse(100))
  }
```

#### Step 3: Building the HTTP responses in a `Service`

The [ResponseBuilder](response.md#response-builder) abstraction provides a convenient way of building the HTTP responses
of any type. In fact, `ResponseBuilder` is a function that takes some content and builds an HTTP response of a type
depending on a content. There are plenty of predefined builders that might be used directly.

```scala
 val ok: HttpResponse = Ok("Hello, world!") // text/plain HTTP response with status code 200
```

## Table of Contents

* [Quickstart](quickstart.md)
* [Demo](demo.md)
* [Micros](micro.md)
  * [Finch in Action](micro.md#finch-in-action)
  * [Your REST API as a Monad](micro.md#your-rest-api-as-a-monad)
  * [Micro](micro.md#micro)
  * [Endpoint](micro.md#endpoint)
  * [Custom Request Type](micro.md#custom-request-type)
* [Routes](route.md)
  * [Overview](route.md#overview)
  * [Built-in Routers](route.md#built-in-routers)
    * [Matchers](route.md#matchers)
    * [Extractors](route.md#extractors)
  * [Composing Routers](route.md#composing-routers)
  * [Endpoints](route.md#endpoints)
  * [Filters and Endpoints](route.md#filters-and-endpoints)
* [Endpoints](endpoint.md)
* [Requests](request.md)
  * [Custom Request Types](request.md#custom-request-types)
  * [Request Reader](request.md#request-reader) 
    * [Overview](request.md#overview)
    * [API](request.md#api)
  * [Base Readers](request.md#base-readers)
    * [Required and Optional Readers](request.md#required-and-optional-readers)
    * [Multi-Value Parameters](request.md#multi-value-parameters)
    * [Custom Readers](request.md#custom-readers)
    * [Error Handling](request.md#error-handling)
  * [Combining and Reusing Readers](request.md#combining-and-reusing-readers)
    * [Applicative Syntax](request.md#applicative-syntax)
    * [Monadic Syntax](request.md#monadic-syntax)
  * [Type Conversion](request.md#type-conversion)
    * [Built-in Decoders](request.md#built-in-decoders)
    * [Custom Decoders](request.md#custom-decoders)
    * [Integration with JSON Libraries](request.md#integration-with-json-libraries)
  * [Validation](request.md#validation)
    * [Inline Validation](request.md#inline-validation)
    * [Reusable Rules](request.md#reusable-validators)
    * [Built-in Rules](request.md#built-in-rules)
* [Responses](response.md)
  * [Response Builder](response.md#response-builder)
  * [HTTP Redirects](response.md#redirects)
* [Authentication](auth.md)
  * [OAuth2](auth.md#authorization-with-oauth2)
  * [Basic Auth](auth.md#basic-http-auth)
* [JSON](json.md)
  * [Finch Json](json.md#finch-json)
  * [Argonaut](json.md#argonaut)
  * [Jawn](json.md#jawn)

[1]: http://twitter.github.io/finagle/
