## Cookbook

* [Fixing `.toService` compile error](cookbook.md#fixing-toservice-compile-error)
* [Serving static content](cookbook.md#serving-static-content)
* [Converting `Error.RequestErrors` into JSON](cookbook.md#converting-errorrequesterrors-into-json)

This is a collection of short recipes of "How to X in Finch".

### Fixing `.toService` compile error

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

--
Read Next: [Best Practices](best-practices.md)

[nginx]: http://nginx.org/en/
[circe]: https://github.com/travisbrown/circe
