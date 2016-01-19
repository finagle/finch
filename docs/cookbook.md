## Cookbook

* [Fixing the `.toService` compile error](cookbook.md#fixing-the-toservice-compile-error)
* [Serving static content](cookbook.md#serving-static-content)
* [Converting `Error.RequestErrors` into JSON](cookbook.md#converting-errorrequesterrors-into-json)
* [Defining endpoints returning empty responses](cookbook.md#defining-endpoints-returning-empty-responses)
* [Defining redirecting endpoints](cookbook.md#defining-redirecting-endpoints)
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

For example, to bring the [Crice][circe] support and benefit from its auto-derivation of codecs
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

val empty: Endpoint[Unit] = get("empty" / string) { s: String =>
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
val fooOrEmpty: Endpoint[Foo] = get("foo" / string) { s: String =>
  if (s != "") Ok(Foo(s))
  else NoContent
}

// This is recommend to do 
val fooOrFailure: Endpoint[Foo] = get("foo" / string) { s: String =>
  if (s != "") Ok(Foo(s))
  else BadRequest(new IllegalArgumentException("empty string"))
}
```

### Defining redirecting endpoints

Redirects are still weird in Finch. Util [reversed routes/endpoints][issue 191] are shipped, the
reasonable way of defining redirecting endpoints is to represent them as `Endpoint[Unit]` (empty
output) indicating that there is no payload returned.

```scala
import io.finch._
import com.twitter.finagle.http.Status

val redirect: Endpoint[Unit] = get("redirect" / "from") {
  Output.unit(Status.SeeOther).withHeader("Location" -> "/redirect/to")
}
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
