---
layout: docs
title: Cookbook
position: 2
---

## Cookbook

This is a collection of short recipes of "How to X in Finch".

* [Fixing the `.toService` compile error](#fixing-the-toservice-compile-error)
* [Serving static content](#serving-static-content)
* [Converting `Error.Multiple` into JSON](#converting-errormultiple-into-json)
* [Defining endpoints returning empty responses](#defining-endpoints-returning-empty-responses)
* [Defining redirecting endpoints](#defining-redirecting-endpoints)
* [Defining custom endpoints](#defining-custom-endpoints)
* [CORS in Finch](#cors-in-finch)
* [Creating a Finagle Client](#creating-a-finagle-client)
* [Converting between Scala futures and Twitter futures](#converting-between-scala-futures-and-twitter-futures)
* [Server Sent Events](#server-sent-events)
* [JSONP](#jsonp)
* [OAuth2](#oauth2)
* [Basic HTTP Auth](#basic-http-auth)

### Fixing the `.toService` compile error

Finch promotes a type-full functional programming style, where an API server is represented as a
coproduct of all the possible types it might return. That said, a Finch server is type-checked
by the compiler to ensure that it's known how to convert every part of coproduct into an HTTP
response. Simply speaking, as a Finch user, you get a compile-time guarantee that for every
endpoint in your application it's possible to find an appropriate encoder. Otherwise you will
get a compile error that looks like this.

```
<console>:34: error: An Endpoint you're trying to convert into a Finagle service is missing one or more encoders.

  Make sure shapeless.:+:[Foo,shapeless.:+:[Bar,shapeless.CNil]] is one of the following:

  * A com.twitter.finagle.http.Response
  * A value of a type with an io.finch.Encode instance (with the corresponding content-type)
  * A coproduct made up of some combination of the above

       (e :+: q).toServiceAs[Application.Json]
```

Which means: the compiler wasn't able to find an instance of `Encode.Json` type-class for types
`Foo` and `Bar`. To fix that you could either provide that instance (seriously, don't do that unless
you have an absolutely specific use case) or use one of the supported JSON libraries and get it for
free (preferred).

For example, to bring the [Circe][circe] support and benefit from its auto-derivation of codecs
you'd only need to add two extra imports to the scope (file) where you call the `.toService` method.

```tut:silent
import io.circe.generic.auto._
import io.finch.circe._
```

### Serving static content

Finch was designed with type-classes powered _extensibility_ in mind, which means it's possible to
define an `Endpoint` of any type `A` as long as there is a type-class instance of `Encode[A]`
available for that type. Needless to say, it's pretty much straightforward to define a _blocking_
instance of `Encode[File]` that turns a given `File` into a `Buf`. Although, it might be tricky to
come up with a _non-blocking_ way of serving static content with Finch, there is a way. The
cornerstone idea is to return a `Buf` instance from the endpoint so we could use an identity
`Encode[Buf]`, thereby lifting the encoding part onto the endpoint itself
(where it's quite legal to return a `Future[Buf]`).

```tut:silent
import cats.effect._
import io.finch._
import io.finch.catsEffect._
import com.twitter.io.{Buf, BufReader, Reader}
import java.io.File

val reader: Reader[Buf] = Reader.fromFile(new File("/dev/urandom"))

val file: Endpoint[IO, Buf] = get("file") {
  BufReader.readAll(reader).map(Ok)
}
```

**Note:** It's usually not a great idea to use tools like Finch (or similar) to serve static
content given their _dynamic_ nature. Instead, a static HTTP server (i.e., [Nginx][nginx]) would be
the perfect fit.

It's also possible to _stream_ the file content to the client using [`AsyncStream`][as].

```tut:silent
import io.finch._
import io.finch.catsEffect._
import com.twitter.conversions.StorageUnitOps._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Http
import com.twitter.io.{Buf, BufReader, Reader}

import java.io.File

val reader: Reader[Buf] = Reader.fromFile(new File("/dev/urandom"))

val file: Endpoint[IO, AsyncStream[Buf]] = get("stream-of-file") {
  val chunkedReader = BufReader.chunked(reader, chunkSize = 512.kilobytes.inBytes.toInt)
  Ok(Reader.toAsyncStream(chunkedReader))
}
```

### Converting `Errors` into JSON

Finch's own errors are often accumulated in the product `Endpoint` and represented as
`io.finch.Errors` that wraps a `cats.data.NonEmptyList[Error]`. Writing an exception handling
function for both `Error` (single error) and `Errors` (multiple errors) cases may not seem as a
trivial thing to do.

With [Circe][circe] the complete implementation might look like the following.

```tut:silent
import io.circe.{Encoder, Json}
import io.finch._
import io.finch.circe._

def errorToJson(e: Error): Json = e match {
  case Error.NotPresent(_) =>
    Json.obj("error" -> Json.fromString("something_not_present"))
  case Error.NotParsed(_, _, _) =>
    Json.obj("error" -> Json.fromString("something_not_parsed"))
  case Error.NotValid(_, _) =>
    Json.obj("error" -> Json.fromString("something_not_valid"))
}

implicit val ee: Encoder[Exception] = Encoder.instance {
  case e: Error => errorToJson(e)
  case Errors(nel) => Json.arr(nel.toList.map(errorToJson): _*)
}
```

### Defining endpoints returning empty responses

Just like in any Scala program you can define a function returning an empty result (a unit
value), in Finch, you can define an endpoint returning an empty response (an empty/unit output).
An `Endpoint[Unit]` represents an endpoint that doesn't return any payload in the response.

```tut:silent
import io.finch._

val empty: Endpoint[IO, Unit] = get("empty" :: path[String]) { s: String =>
  NoContent[Unit].withHeader("X-String" -> s)
}
```

There are also cases when an endpoint returns either a payload or an empty response. While it's
probably a better idea to use failures in order to explain to the remote client why there is no
payload in the response, it's totally possible to send empty ones instead.

```tut:silent
import io.finch._
import com.twitter.finagle.http.Status

case class Foo(s: String)

// This is possible
val fooOrEmpty: Endpoint[IO, Foo] = get("foo" :: path[String]) { s: String =>
  if (s != "") Ok(Foo(s))
  else NoContent
}

// This is recommended
val fooOrFailure: Endpoint[IO, Foo] = get("foo" :: path[String]) { s: String =>
  if (s != "") Ok(Foo(s))
  else BadRequest(new IllegalArgumentException("empty string"))
}
```

### Defining redirecting endpoints

Redirects are still weird in Finch. Until [reversed routes/endpoints][issue191] are shipped, the
reasonable way of defining redirecting endpoints is to represent them as `Endpoint[Unit]` (empty
output) indicating that there is no payload returned.

```tut:silent
import io.finch._
import com.twitter.finagle.http.Status

val redirect: Endpoint[IO, Unit] = get("redirect" :: "from") {
  Output.unit(Status.SeeOther).withHeader("Location" -> "/redirect/to")
}
```

### Defining custom endpoints

"Custom endpoints" isn't probably a good definition for these since once you called `map*` or `as*`
on a predefined endpoint it becomes "custom". Anyways, there are endpoints (at least some part of
more complex endpoints) that might be decoupled and shared across other endpoints in your
application.

Finch is a library promoting functional programming, which means it prefers composition over
inheritance. Thus, building new instances in Finch is never about extending some base class, but
about composing existing instances together.

**Example 1: aka request reader**

Before 0.10, there was a `RequestReader` abstraction in Finch that has been replaced with
_evaluating endpoints_. Even though the name was changed, the request-reader-flavored API (and
behavior) wasn't touched at all.

In the following example, we define a new endpoint `foo` that reads an instance of the case class
`Foo` from the request during the _evaluation_ stage. So it won't affect matching.

```tut:silent
import io.finch._

case class Foo(i: Int, s: String)

val foo: Endpoint[IO, Foo] = (param[Int]("i") :: param("s")).as[Foo]

val getFoo: Endpoint[IO, Foo] = get("foo" :: foo) { f: Foo =>
  println(s"Got foo: $f")
  Ok(f) // echo it back
}
```

**Note:** The endpoint body from the example above will never be evaluated if the `foo` endpoint
fails (e.g., one of the params is missing). This shouldn't be a big surprise given that such
behavior is quite natural for a functor (i.e., `map` function) - an endpoint on which `mapOutput` is
called (via the syntactic sugar around `apply`) might have already failed.

**Example 2: authentication**

Since endpoints provide more control over the output (i.e., via `io.finch.Output`), it's now
possible to define self-contained instances that also handle exceptions (convert them to appropriate
outputs).

In this example, we define an evaluating endpoint `auth` that takes a request and tries to
authenticate it by the user name passed in the `User` header. If the header is missing, the request
is considered unauthorized.

```tut:silent
import io.finch._

case class User(id: Int)

val auth: Endpoint[IO, User] = header("User").mapOutput(u =>
  if (u == "secret user") Ok(User(10))
  else Unauthorized(new Exception(s"User $u is unknown."))
).handle {
  // if header "User" is missing we respond 401
  case e: Error.NotPresent => Unauthorized(e)
}

val getCurrentUser: Endpoint[IO, User] = get("user" :: auth) { u: User =>
  println(s"Got user: $u")
  Ok(u) // echo it back
}
```

**Note:** Even though an endpoint `auth` can't fail, since we explicitly handled its only possible
exception, the body of the `getCurrentUser` endpoint will only be evaluated if the incoming request
contains a header `User: secret user` and a path `/user`. This comes from `io.finch.Output`, which
provides a monadic API over the three cases (payload (i.e., `Ok`), failure (i.e., `BadRequest`) and
empty) and only `Output.Payload` is considered a success. Simply speaking, calling `map*` on either
`Output.Failure` or `Output.Empty` is the same as calling `map*` on `None: Option[Nothing]`. Thus,
an endpoint returning non-`Output.Payload` output is considered failed and its `map*` call won't be
evaluated.

**Example 3: asynchronous authentication**

Sometimes authenticating a request requires an asynchronous call (e.g., to a database or another
HTTP service). Luckily, in addition to the `mapOutput` method used in the previous example, which
takes a function of type `(A) => Output[B]`, `Endpoint`s also have a method called `mapOutputAsync`
that takes a function of type `(A) => Future[Output[B]]`.

The previous example's `auth` endpoint can be updated as follows:

```tut:silent
import com.twitter.util.Future

def fetchUserForToken(token: String): IO[Option[User]] = ???

val auth: Endpoint[IO, User] = header("User").mapOutputAsync(u =>
  if (u == "secret user") IO.pure(Ok(User(10)))
  else fetchUserForToken(u).map {
    case Some(user) => Ok(user)
    case None => Unauthorized(new Exception(s"Invalid token: $u"))
  }
).handle {
  // if header "User" is missing we respond 401
  case e: Error.NotPresent => Unauthorized(e)
}
```

The `getCurrentUser` endpoint doesn't need to change at all, since `auth` is still an
`Endpoint[User]`.

**Example 4: custom path matcher**

Let's say you want to write a custom _matching_ endpoint that only matches requests whose current
path segment might be extracted as (converted to) Java 8's `LocalDateTime`.

```tut:silent
import io.finch._
import com.twitter.util.Try
import java.time.LocalDateTime

implicit val e: DecodePath[LocalDateTime] =
  DecodePath.instance(s => Try(LocalDateTime.parse(s)).toOption)

val dateTime: Endpoint[IO, LocalDateTime] = get("time" :: path[LocalDateTime]) { t: LocalDateTime =>
  println(s"Got time: $t")
  Ok(t) // echo it back
}
```

**Note:** `io.finch.DecodePath` is an experimental API that will be (or not) eventually promoted
to non-experimental.

**Example 5: get all parameters or headers**

In case if you want to operate with a whole request, you could use `root` endpoint. But if there is
need to get only query parameters or headers, it's easy to reuse this endpoint to get something like
following:

```tut
import io.finch._

val headersAll = root.map(_.headerMap.toMap)

val headers = get("hello" :: headersAll) {headers: Map[String, String] =>
  Ok(s"Headers: $headers")
}

headers(Input.get("/hello").withHeaders("foo" -> "bar")).awaitValueUnsafe()
```

### CORS in Finch

There is a [Finagle filter][cors-filter] which, when applied, enriches a given HTTP service with
[CORS][cors] behavior. The following example builds a CORS filter that allows `GET` and `POST`
requests with an `Accept` header from any origin.

```tut:silent
import com.twitter.finagle.http.filter.Cors
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Service
import io.finch._
import io.finch.catsEffect._

val service: Service[Request, Response] = 
  Endpoint.liftOutput[IO, String](Ok("Hello, world!")).toServiceAs[Text.Plain]

val policy: Cors.Policy = Cors.Policy(
  allowsOrigin = _ => Some("*"),
  allowsMethods = _ => Some(Seq("GET", "POST")),
  allowsHeaders = _ => Some(Seq("Accept"))
)

val corsService: Service[Request, Response] = new Cors.HttpFilter(policy).andThen(service)
```

### Creating a Finagle Client

Since Finch is built on top of Finagle, it shares its utilities like the stateless [client][client]. Do read the Finagle's
documentation to get a full understanding of the configuration options for the client e.g. [loadbalancing][loadbalancing] abilities. A simple example is given below.

```tut:silent
import com.twitter.finagle.Http
import com.twitter.finagle.http._
import com.twitter.util.{Await, Future}

val host = "api.nasa.gov"
val client: Service[Request, Response] = Http.client.withTls(host).newService(s"$host:443")
val request = Request(Method.Get, "/planetary/apod?api_key=DEMO_KEY")
val response: Future[Response] = client(request)

Await.result(response.onSuccess { rep: Response =>
  println("GET success: " + rep)
})

```



### Converting between Scala futures and Twitter futures

Another shared utility are Twitter's [futures][futures]. While
there is already an official tool for performing conversions between Scala futures and Twitter
futures (i.e., [Twitter Bijection][bijection]), it usually makes sense to avoid an extra dependency
because of a couple of functions which are fairly easy to implement.

```tut:silent
import com.twitter.util.{Future => TFuture, Promise => TPromise, Return, Throw}
import scala.concurrent.{Future => SFuture, Promise => SPromise, ExecutionContext}
import scala.util.{Success, Failure}

implicit class RichTFuture[A](f: TFuture[A]) {
  def asScala(implicit e: ExecutionContext): SFuture[A] = {
    val p: SPromise[A] = SPromise()
    f.respond {
      case Return(value) => p.success(value)
      case Throw(exception) => p.failure(exception)
    }

    p.future
  }
}

implicit class RichSFuture[A](f: SFuture[A]) {
  def asTwitter(implicit e: ExecutionContext): TFuture[A] = {
    val p: TPromise[A] = new TPromise[A]
    f.onComplete {
      case Success(value) => p.setValue(value)
      case Failure(exception) => p.setException(exception)
    }

    p
  }
}
```

Also note that as of [Finch 0.16-M3](https://github.com/finagle/finch/releases/tag/0.16.0-M3) there
is a Scala Futures syntax support for endpoints.

```tut:silent
import io.finch._, io.finch.catsEffect._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val e = get("foo") { Future.successful(Ok("bar")) }

e(Input.get("/foo")).awaitValueUnsafe()
```

### Server Sent Events

Finch offers support for [Server Sent Events][server-sent-events] through the `finch-sse` sub-project.
Server Sent Events are represented as `AsyncStream`s and streamed over the chunked HTTP transport.

The `ServerSentEvent` case class carries an arbitrary `data` field and it's possible to encode any
`ServerSentEvent[A]` for which `cats.Show[A]` is defined.

In this example, every next second we stream instances of `java.util.Date` as server sent events on
the `time` endpoint.

NOTE: SSE requires `Cache-Control` to be disabled.

```tut:silent
import cats.Show
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch._
import io.finch.fs2._
import _root_.fs2.Stream
import java.util.Date
import scala.concurrent.duration._

implicit val showDate: Show[Date] = Show.fromToString[Date]

implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)
def streamTime(): Stream[IO, ServerSentEvent[Date]] =
  Stream.awakeEvery[IO](1.second).map(_ => new Date()).map(ServerSentEvent(_))

val time: Endpoint[IO, Stream[IO, ServerSentEvent[Date]]] = get("time") {
  Ok(streamTime()).withHeader("Cache-Control" -> "no-cache")
}

val service: Service[Request, Response] = time.toServiceAs[Text.EventStream]
```

### JSONP

Not going into the details on why [JSONP considered insecure][insecure-jsonp], there is a Finagle
filter `JsonpFilter` that can be applied to an HTTP service returning JSON to "upgrade" it to JSONP.

Here is a small example on how to wire this filter with Finch's endpoint.

```tut:silent
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.http.filter.JsonpFilter
import io.finch._
import io.finch.circe._
import io.circe.generic.auto._

val endpoint: Endpoint[IO, Map[String, String]] = get("jsonp") {
  Ok(Map("foo" -> "bar"))
}

val service: Service[Request, Response] =
  JsonpFilter.andThen(endpoint.toServiceAs[Application.Json])
```

`JsonpFilter` is dead simple. It checks the returned HTTP payload and if it's a JSON string, wraps
it with a call to a function whose name is passed in the `callback` query-string param (and changes
the content-type to `application/javascript` correspondingly). Using [HTTPie][httpie], this would look as:

```
$ http :8081/jsonp
HTTP/1.1 200 OK
Content-Encoding: gzip
Content-Length: 39
Content-Type: application/json

{
    "foo": "bar"
}

$ http :8080/jsonp?callback=myfunction
HTTP/1.1 200 OK
Content-Encoding: gzip
Content-Length: 56
Content-Type: application/javascript

/**/myfunction({"foo":"bar"});
```

### OAuth2

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is
supported in Finch via the [finch-oauth2](https://github.com/finch/finch-oauth2) package.

### Basic HTTP Auth

[Basic HTTP Auth][http-auth] support could be added via the [finagle-http-auth][finagle-http-auth]
project that provides Finagle filters implementing authentication for clients and servers. In Finch
this would look like a `BasicAuth.Server` filter applied to `Service` returned from the `.toService`
call. See finagle-http-auth's README for more usage details.

[nginx]: http://nginx.org/en/
[circe]: https://github.com/circe/circe
[issue191]: https://github.com/finagle/finch/issues/191
[client]: http://twitter.github.io/finagle/guide/Clients.html
[loadbalancing]: http://twitter.github.io/finagle/guide/Clients.html#load-balancing
[futures]: http://twitter.github.io/finagle/guide/Futures.html
[bijection]: https://github.com/twitter/bijection
[as]: https://github.com/twitter/util/blob/develop/util-core/src/main/scala/com/twitter/concurrent/AsyncStream.scala
[cors-filter]: https://github.com/twitter/finagle/blob/develop/finagle-http/src/main/scala/com/twitter/finagle/http/filter/Cors.scala
[cors]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
[server-sent-events]: https://en.wikipedia.org/wiki/Server-sent_events
[insecure-jsonp]: http://erlend.oftedal.no/blog/?blogid=97
[http-auth]: http://en.wikipedia.org/wiki/Basic_access_authentication
[finagle-http-auth]: https://github.com/finagle/finagle-http-auth
[argonaut]: http://argonaut.io
[jackson]: http://wiki.fasterxml.com/JacksonHome
[json4s]: http://json4s.org/
[circe-jackson]: https://github.com/circe/circe-jackson
[playjson]: https://www.playframework.com/documentation/2.4.x/ScalaJson
[spray-json]: https://github.com/spray/spray-json
[circe-jackson-performance]: https://github.com/circe/circe-jackson#jackson-vs-jawn
[httpie]: https://httpie.org/
