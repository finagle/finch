## Cookbook

* [JSON](cookbook.md#json)
 * [Circe](cookbook.md#circe)
 * [Argonaut](cookbook.md#argonaut)
 * [Jackson](cookbook.md#jackson)
 * [Json4s](cookbook.md#json4s)
 * [PlayJson](cookbook.md#playjson)
* [Fixing the `.toService` compile error](cookbook.md#fixing-the-toservice-compile-error)
* [Serving multiple content types](cookbook.md#serving-multiple-content-types)
* [Serving static content](cookbook.md#serving-static-content)
* [Converting `Error.Multiple` into JSON](cookbook.md#converting-errormultiple-into-json)
* [Defining endpoints returning empty responses](cookbook.md#defining-endpoints-returning-empty-responses)
* [Defining redirecting endpoints](cookbook.md#defining-redirecting-endpoints)
* [Defining custom endpoints](cookbook.md#defining-custom-endpoints)
* [CORS in Finch](cookbook.md#cors-in-finch)
* [Converting between Scala futures and Twitter futures](cookbook.md#converting-between-scala-futures-and-twitter-futures)
* [Server Sent Events](cookbook.md#server-sent-events)
* [JSONP](cookbook.md#jsonp)
* [OAuth2](cookbook.md#oauth2)
* [Basic HTTP Auth](cookbook.md#basic-http-auth)

This is a collection of short recipes of "How to X in Finch".

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

```scala
import io.finch.circe._
import io.circe.generic.auto._
```

It's also possible to import the Circe configuration which uses a pretty printer configured with
`dropNullKeys = true`. Use the following imports instead:

```scala
import io.finch.circe.dropNullKeys._
import io.circe.generic.auto._
```

Unless it's absolutely necessary to customize Circe's output format (i.e., drop null keys), always
prefer the [Jackson serializer][circe-jackson] for [better performance][circe-jackson-performance].
The following two imports show how to make Circe use Jackson while serializing instead of the
built-in pretty printer.

```scala
import io.finch.circe.jacksonSerializer._
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

case class Foo(name: String,age: Int)

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

```scala
import io.circe.generic.auto._
import io.finch.circe._
```

### Serving multiple content types

In its current form (as per 0.11) Finch natively supports only single content type per Finagle
service. This restriction is encoded in the API such that `toServiceAs` call only takes single type
parameter, single content type.

Even though this restriction is temporary and should be removed in 0.11 (or 1.0), there is an
essential workaround in Finch that will always be supported (even after 1.0). The general idea is
to downgrade an endpoint that returns a payload of a content type that's different from one passed
to the `toServiceAs` call to `Endpoint[Response]`.

An `Endpoint[Response]` has a special meaning:

- It's not necessary to wrap a `Response` with `Output` when mapping endpoint
- There is an identity (pass through) encoder for `Response` defined in Finch

That said, Finagle's `Response`s have (and will always be having) a first-class support in Finch to
accommodate use cases when decoupling from HTTP types and primitives implies unnecessary complexity
or simply not possible in the current implementation.

Putting it all together and assuming that most of the Finch applications serve JSON payloads, it's
reasonable to pass  `Application.Json` to the `toServiceAs` call and downgrade non-JSON endpoints to
`Endpoint[Response]`.

```scala
import com.twitter.finagle.Http
import com.twitter.finagle.http.Response
import com.twitter.io.Buf
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

case class Message(message: String)

val json: Endpoint[Message] = get("json") {
  Ok(Message("Hello, World!"))
}

val text: Endpoint[Response] = get("text") {
  val rep = Response()
  rep.content = Buf.Utf8("Hello, World!")
  rep.contentType = "text/plain"

  rep
}

Http.server.serve(":8081", (json :+: text).toServiceAs[Application.Json])
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

```scala
import io.finch._
import com.twitter.io.{Reader, Buf}
import com.twitter.finagle.Http
import com.twitter.util.Await
import java.io.File

val reader: Reader = Reader.fromFile(new File("/dev/urandom"))

val file: Endpoint[Buf] = get("file") {
  Reader.readAll(reader).map(Ok)
}

Await.ready(Http.server.serve(":8081", file.toServiceAs[Text.Plain]))
```
**Note:** It's usually not a great idea to use tools like Finch (or similar) to serve static
content given their _dynamic_ nature. Instead, a static HTTP server (i.e., [Nginx][nginx]) would be
the perfect fit.

It's also possible to _stream_ the file content to the client using [`AsyncStream`][as].

```scala
import io.finch._
import com.twitter.conversions.storage._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Http
import com.twitter.io.{Reader, Buf}

import java.io.File

val reader: Reader = Reader.fromFile(new File("/dev/urandom"))

val file: Endpoint[AsyncStream[Buf]] = get("stream-of-file") {
  Ok(AsyncStream.fromReader(reader, chunkSize = 512.kilobytes.inBytes.toInt))
}

Http.server
  .withStreaming(enabled = true)
  .serve(":8081", file.toServiceAs[Text.Plain])
```

### Converting `Errors` into JSON

Finch's own errors are often accumulated in the product `Endpoint` and represented as
`io.finch.Errors` that wraps a `cats.data.NonEmptyList[Error]`. Writing an exception handling
function for both `Error` (single error) and `Errors` (multiple errors) cases may not seem as a
trivial thing to do.

With [Circe][circe] the complete implementation might look like the following.

```scala
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

```scala
import io.finch._

val empty: Endpoint[Unit] = get("empty" :: string) { s: String =>
  NoContent[Unit].withHeader("X-String" -> s)
}
```

There are also cases when an endpoint returns either a payload or an empty response. While it's
probably a better idea to use failures in order to explain to the remote client why there is no
payload in the response, it's totally possible to send empty ones instead.

```scala
import io.finch._
import com.twitter.finagle.http.Status

case class Foo(s: String)

// This is possible
val fooOrEmpty: Endpoint[Foo] = get("foo" :: string) { s: String =>
  if (s != "") Ok(Foo(s))
  else NoContent
}

// This is recommended
val fooOrFailure: Endpoint[Foo] = get("foo" :: string) { s: String =>
  if (s != "") Ok(Foo(s))
  else BadRequest(new IllegalArgumentException("empty string"))
}
```

### Defining redirecting endpoints

Redirects are still weird in Finch. Until [reversed routes/endpoints][issue191] are shipped, the
reasonable way of defining redirecting endpoints is to represent them as `Endpoint[Unit]` (empty
output) indicating that there is no payload returned.

```scala
import io.finch._
import com.twitter.finagle.http.Status

val redirect: Endpoint[Unit] = get("redirect" :: "from") {
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

```scala
case class Foo(i: Int, s: String)

val foo: Endpoint[Foo] = (param("i").as[Int] :: param("s")).as[Foo]

val getFoo: Endpoint[Foo] = get("foo" :: foo) { f: Foo =>
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

```scala
import io.finch._

case class User(id: Int)

val auth: Endpoint[User] = header("User").mapOutput(u =>
  if (u == "secret user") Ok(User(10))
  else Unauthorized(new Exception(s"User $u is unknown."))
).handle {
  // if header "User" is missing we respond 401
  case e: Error.NotPresent => Unauthorized(e)
}

val getCurrentUser: Endpoint[User] = get("user" :: auth) { u: User =>
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

```scala
def fetchUserForToken(token: String): Future[Option[User]] = ???

val auth: Endpoint[User] = header("User").mapOutputAsync(u =>
  if (u == "secret user") Future.value(Ok(User(10)))
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

```scala
import io.finch._
import com.twitter.util.Try
import java.time.LocalDateTime

implicit val e: DecodePath[LocalDateTime] =
  DecodePath.instance(s => Try(LocalDateTime.parse(s)).toOption)

val dateTime: Endpoint[LocalDateTime] = get("time" :: path[LocalDateTime]) { t: LocalDateTime =>
  println(s"Got time: $t")
  Ok(t) // echo it back
}
```

**Note:** `io.finch.DecodePath` is an experimental API that will be (or not) eventually promoted
to non-experimental.


### CORS in Finch

There is a [Finagle filter][cors-filter] which, when applied, enriches a given HTTP service with
[CORS][cors] behavior. The following example builds a CORS filter that allows `GET` and `POST`
requests with an `Accept` header from any origin.

```scala
import com.twitter.finagle.http.filter.Cors
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Service
import io.finch._

val service: Service[Request, Response] = Endpoint(Ok("Hello, world!")).toService

val policy: Cors.Policy = Cors.Policy(
  allowsOrigin = _ => Some("*"),
  allowsMethods = _ => Some(Seq("GET", "POST")),
  allowsHeaders = _ => Some(Seq("Accept"))
)

val corsService: Service[Request, Response] = new Cors.HttpFilter(policy).andThen(service)
```

### Converting between Scala futures and Twitter futures

Since Finch is built on top of Finagle, it shares its utilities, including [futures][futures]. While
there is already an official tool for performing conversions between Scala futures and Twitter
futures (i.e., [Twitter Bijection][bijection]), it usually makes sense to avoid an extra dependency
because of a couple of functions which are fairly easy to implement.

```scala
import com.twitter.util.{Future => TFuture, Promise => TPromise, Return, Throw}
import scala.concurrent.{Future => SFuture, Promise => SPromise, ExecutionContext}
import scala.util.{Success, Failure}

implicit class RichTFuture[A](val f: TFuture[A]) extends AnyVal {
  def asScala(implicit e: ExecutionContext): SFuture[A] = {
    val p: SPromise[A] = SPromise()
    f.respond {
      case Return(value) => p.success(value)
      case Throw(exception) => p.failure(exception)
    }

    p.future
  }
}

implicit class RichSFuture[A](val f: SFuture[A]) extends AnyVal {
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

### Server Sent Events

Finch offers support for [Server Sent Events](server-sent-events) through the `finch-sse` sub-project.
Server Sent Events are represented as `AsyncStream`s and streamed over the chunked HTTP transport.

The `ServerSentEvent` case class caries an arbitrary `data` field and it's possible to encode any
`ServerSentEvent[A]` for which `cats.Show[A]` is defined.

In this example, every next second we stream instances of `java.util.Date` as server sent events on
the `time` endpoint.

NOTE: SSE requires `Cache-Control` to be disabled.

```scala
import cats.Show
import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.finagle.Http
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.{ Await, Future, Timer}
import io.finch._
import io.finch.sse._
import java.util.Date

implicit val showDate: Show[Date] = Show.fromToString[Date]

implicit val timest: Timer = DefaultTimer.twitter

def streamTime(): AsyncStream[ServerSentEvent[Date]] =
  AsyncStream.fromFuture(
    Future.sleep(1.seconds)
          .map(_ => new Date())
          .map(ServerSentEvent(_))
  ) ++ streamTime()

val time: Endpoint[AsyncStream[ServerSentEvent[Date]]] = get("time") {
  Ok(streamTime())
    .withHeader("Cache-Control" -> "no-cache")
}

Await.ready(Http.server.serve(":8081", time.toServiceAs[Text.EventStream]))
```

### JSONP

Not going into the details on why [JSONP considered insecure][insecure-jsonp], there is a Finagle
filter `JsonpFilter` that can be applied to an HTTP service returning JSON to "upgrade" it to JSONP.

Here is a small example on how to wire this filter with Finch's endpoint.

```scala
import com.twitter.finagle.Http
import com.twitter.finagle.http.filter.JsonpFilter
import io.finch._
import io.finch.circe._

val endpoint: Endpoint[Map[String, String]] = get("jsonp") {
  Ok(Map("foo" -> "bar"))
}

val service = endpoint.toServiceAs[Application.Json]

Http.server.serve(":8080", JsonpFilter.andThen(service))
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
supported in Finch via the `finch-oauth2` package:

*Authorize*
```scala
import com.twitter.finagle.oauth2._
import io.finch.oauth2._

val dataHandler: DataHandler[Int] = ???
val auth: Endpoint[AuthInfo[Int]] = authorize(dataHandler)
val e: Endpoint[Int] = get("user" :: auth) { ai: AuthInfo[Int] => Ok(ai.user) }
```

*Issue Access Token*
```scala
import com.twitter.finagle.oauth2._
import io.finch.oauth2._

val token: Endpoint[GrantHandlerResult] = issueAccessToken(dataHandler)
```

Note that both `token` and `authorize` may throw `com.twitter.finagle.oauth2.OAuthError`, which is
already _handled_ by a returned endpoint but needs to be serialized. This means you might want to
include its serialization logic into an instance of `Encode[Exception]`.

### Basic HTTP Auth

[Basic HTTP Auth][http-auth] support could be added via the [finagle-http-auth][finagle-http-auth]
project that provides Finagle filters implementing authentication for clients and servers. In Finch
this would look like a `BasicAuth.Server` filter applied to `Service` returned from the `.toService`
call. See finagle-http-auth's README for more usage details.

--
Read Next: [Best Practices](best-practices.md)

[nginx]: http://nginx.org/en/
[circe]: https://github.com/circe/circe
[issue191]: https://github.com/finagle/finch/issues/191
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
