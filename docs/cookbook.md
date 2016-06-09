## Cookbook

* [Fixing the `.toService` compile error](cookbook.md#fixing-the-toservice-compile-error)
* [Serving static content](cookbook.md#serving-static-content)
* [Converting `Error.RequestErrors` into JSON](cookbook.md#converting-errorrequesterrors-into-json)
* [Defining endpoints returning empty responses](cookbook.md#defining-endpoints-returning-empty-responses)
* [Defining redirecting endpoints](cookbook.md#defining-redirecting-endpoints)
* [Defining custom endpoints](cookbook.md#defining-custom-endpoints)
* [CORS in Finch](cookbook.md#cors-in-finch)
* [Converting between Scala futures and Twitter futures](cookbook.md#converting-between-scala-futures-and-twitter-futures)

This is a collection of short recipes of "How to X in Finch".

### Fixing the `.toService` compile error

Finch promotes a type-full functional programming style, where an API server is represented as
coproduct of all the possible types it might return. That said, a Finch server is type-checked
by a compiler to ensure that it's known how to convert every part of coproduct into an HTTP
response. Simply speaking, as a Finch user, you get a compile-time grantee that for every
endpoint in your application it's was possible to find an appropriate encoder. Otherwise you will
get a compile error that looks like this.

```
[error] /Users/vkostyukov/e/src/main/scala/io/finch/eval/Main.scala:46:
[error] You can only convert a router into a Finagle service if the result type of the router is one
[error] of the following:
[error]
[error]   * A Response
[error]   * A value of a type with an EncodeResponse instance
[error]   * A coproduct made up of some combination of the above
[error]
[error] io.finch.eval.Main.Output does not satisfy the requirement. You may need to provide an
[error] EncodeResponse instance for io.finch.eval.Main.Output (or for some  part of
[error] io.finch.eval.Main.Output).
```

That means, a compiler wasn't able to find an instance of `EncodeResponse` type-class for type
`Output`. To fix that you could either provide that instance (seriously, don't do that unless you
have an absolutely specific use case) or use one of the supported JSON libraries and get it for
free (preferred).

For example, to bring the [Circe][circe] support and benefit from its auto-derivation of codecs
you'd only need to add two extra imports to the scope (file) where you call the `.toService` method.

```scala
import io.circe.generic.auto._
import io.finch.circe._
```

**Note:** IntelliJ usually marks those imports unused (grey). Don't. Trust. It.

In addition to `EncodeResponse` instance for return (success) types, Finch also requires an instance
for `Exception` (failure) that might be thrown by the endpoint. That said, both failures and
success values should be serialised and propagated to the client over the wire.

It's relatively easy to provide such instance with JSON libraries featuring compile-time reflection
and type-classes for decoding/encoding (Circe, Argonaut). For example, with Circe it might be
defined as follows.

```scala
import io.circe.{Encoder, Json}

implicit val encodeException: Encoder[Exception] = Encoder.instance(e =>
  Json.obj(
    "type" -> Json.string(e.getClass.getSimpleName),
    "message" -> Json.string(e.getMessage)
  )
)
```

Anyways, this may be tricky to do with libraries using runtime-reflection (Jackson, JSON4S) since
they are usually able to serialise `Any` values, which means it's possible to compile a `.toService`
call without an explicitly provided `EncodeResponse[Exception]`. This may lead to some unexpected
results (even `StackOverflowException`s). As a workaround, you might define a raw instance of
`EncodeResponse[Exception]` that wraps a call to the underlying JSON library. The following example,
demonstrates how to do that with Jackson.

```scala
import io.finch._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

implicit val objectMapper: ObjectMapper =
  new ObjectMapper().registerModule(DefaultScalaModule)

implicit val ee: EncodeResponse[Exception] =
  EncodeResponse.fromString("application/json") { e =>
    objectMapper.writeValueAsString(Map("error" -> e.getMessage))
  }
````

### Serving static content

Finch was designed with type-classes powered _extensibility_ in mind, which means it's possible to
define an `Endpoint` of any type `A` as long as there is a type-class instance of`EncodeResponse[A]`
available for that type. Needless to say, it's pretty much straightforward to define a _blocking_
instance of `EncodeResponse[File]` that turns a given `File` into a `Buf`. Although, it might be
tricky to come up with _non-blocking_ way of serving a static content with Finch. The cornerstone
idea is to return a `Buf` instance from the endpoint so we could use an identity `EncodeResponse`,
thereby lifting the encoding part onto endpoint itself (where it's quite legal to return a
`Future[Buf]`).

```scala
import io.finch._
import com.twitter.io.{Reader, Buf}
import java.io.File

val reader: Reader = Reader.fromFile(new File("/dev/urandom"))

val file: Endpoint[Buf] = get("file") {
  Ok(Reader.readAll(reader)).withContentType(Some("text/plain"))
}
```
**Note:** It's usually not a great idea to use tools like Finch (or similar) while serving a static
content given their _dynamic_ nature. Instead, a static HTTP server (i.e., [Nginx][nginx]) would be
a perfect tool to use.

Since Finch 0.10 it's possible to _stream_ the file content to the client using [`AsyncStream`][as].

```scala
import io.finch._
import com.twitter.conversions.storage._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Http
import com.twitter.io.{Reader, Buf}

import java.io.File

val reader: Reader = Reader.fromFile(new File("/dev/urandom"))

val file: Endpoint[AsyncStream[Buf]] = get("stream-of-file") {
  Ok(AsyncStream.fromReader(reader, chunkSize = 512.kilobytes.inBytes))
}

Http.server
  .withStreaming(enabled = true)
  .serve(":8081", file.toService)
```

### Converting `Error.RequestErrors` into JSON

For the sake of errors accumulating, Finch exceptions are encoded into a recursive ADT, where
`Error.RequestErrors` wraps a `Seq[Error]`. Thus is might be tricky to write an encoder (i.e,
`EncodeResponse[Exception]`) for this. Although, this kind of problem shouldn't be a surprise for
Scala programmers, who deal with recursive ADTs and pattern matching on a daily basis.

The general idea is to write a recursive function converting a `Throwable` to `Seq[Json]` (where
`Json` represents an AST in a particular JSON library) and then convert `Seq[Json]` into JSON array.

With [Circe][circe] the complete implementation might look as follows.

```scala
import io.finch._
import io.finch.circe._
import io.circe.{Encoder, Json}

def exceptionToJson(t: Throwable): Seq[Json] = t match {
  case Error.NotPresent(_) =>
    Seq(Json.obj("error" -> Json.string("something_not_present")))
  case Error.NotParsed(_, _, _) =>
    Seq(Json.obj("error" -> Json.string("something_not_parsed")))
  case Error.NotValid(_, _) =>
    Seq(Json.obj("error" -> Json.string("something_not_valid")))
  case Error.RequestErrors(ts) =>
    ts.map(exceptionToJson).flatten
}

implicit val ee: Encoder[Exception] =
  Encoder.instance(e => Json.array(exceptionToJson(e): _*))
```

### Defining endpoints returning empty responses

As well as in any Scala program you can define a function returning an empty result (a unit
value), in Finch, you can define an endpoint returning an empty response (an empty/unit output).
An `Endpoint[Unit]` represents an endpoint that doesn't return any payload in the response.

```scala
import io.finch._

val empty: Endpoint[Unit] = get("empty" :: string) { s: String =>
  NoContent[Unit].withHeader("X-String" -> s)
}
```

There are also cases when an endpoint returns either payload or empty response. While it's probably
a better idea to use failures in order to explain the remote client why there is no payload in the
response, it's totally possible to send empty ones instead.

```scala
import io.finch._
import com.twitter.finagle.http.Status

case class Foo(s: String)

// This possible to do
val fooOrEmpty: Endpoint[Foo] = get("foo" :: string) { s: String =>
  if (s != "") Ok(Foo(s))
  else NoContent
}

// This is recommend to do 
val fooOrFailure: Endpoint[Foo] = get("foo" :: string) { s: String =>
  if (s != "") Ok(Foo(s))
  else BadRequest(new IllegalArgumentException("empty string"))
}
```

### Defining redirecting endpoints

Redirects are still weird in Finch. Util [reversed routes/endpoints][issue191] are shipped, the
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

One way or another, Finch is a library promoting functional programming, which means it prefers
composition over inheritance. Thus, building new instances in Finch is never about extending some
base class, but about composing existing instances together.

**Example 1: aka request reader**

Before 0.10 there was a `RequestReader` abstraction in Finch that has been replaced with _evaluating
endpoints_. Even that the name was changed, the request-reader-flavored API (and behaviour) wasn't
touched at all.

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
called (via the syntactic sugar around `apply`) might be already failed.

**Example 2: authentication**

Since endpoints provide more control over the output (i.e., via `io.finch.Output`), it's now
possible to define self-contained instances that also handle exceptions (convert them to appropriate
outputs).

In this example, we define an evaluating endpoint `auth` that takes a request and tries to
authenticate it by the user name passed in the `User` header. If the header is missing the request
considered unauthorized.

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

**Note:** Even though an endpoint `auth` can't fail since we explicitly handled its only possible
exception, the body of the `getCurrentUser` endpoint will only be evaluated if the incoming request
contains a header `User: secret user` and a path `/user`. This comes from `io.finch.Output`, which
provides a monadic API over the three cases (payload (i.e., `Ok`), failure (i.e., `BadRequest`) and
empty) and only `Output.Payload` is considered a success. Simply speaking, calling `map*` on either
`Output.Failure` or `Output.Empty` is the same as calling `map*` on `None: Option[Nothing]`. Thus,
an endpoint returning non-`Output.Payload` output considered failed and its `map*` call won't be
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
import io.finch.internal
import com.twitter.util.Try
import java.time.LocalDateTime

implicit val e: internal.Capture[LocalDateTime] =
  internal.Capture.instance(s => Try(LocalDateTime.parse(s)).toOption)

val dateTime: Endpoint[LocalDateTime] = get("time" :: path[LocalDateTime]) { t: LocalDateTime =>
  println(s"Got time: $t")
  Ok(t) // echo it back
}
```

**Note:** `io.finch.internal.Extractor` is an experimental API that will be (or not) eventually
promoted to non-experimental.


### CORS in Finch

There is a [Finagle filter][cors-filter] that's when applied, enriches a given HTTP service with
[CORS][cors] behaviour. The following example builds a CORS filter that allows `GET` and `POST`
requests with `Accept` header from any origin.

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
because of a couple of functions, which are fairly easy to implement.

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

--
Read Next: [Best Practices](best-practices.md)

[nginx]: http://nginx.org/en/
[circe]: https://github.com/travisbrown/circe
[issue191]: https://github.com/finagle/finch/issues/191
[futures]: http://twitter.github.io/finagle/guide/Futures.html
[bijection]: https://github.com/twitter/bijection
[as]: https://github.com/twitter/util/blob/develop/util-core/src/main/scala/com/twitter/concurrent/AsyncStream.scala
[cors-filter]: https://github.com/twitter/finagle/blob/develop/finagle-http/src/main/scala/com/twitter/finagle/http/filter/Cors.scala
[cors]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS

