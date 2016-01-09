## Cookbook

* [Serving static content](cookbook.md#serving-static-content)
* [Converting `Error.RequestErrors` into JSON](cookbook.md#converting-errorrequesterrors-into-json)

This is a collection of short recipes of "How to X in Finch".

### Serving static content

Finch was designed with type-classes powered _extensibility_ in mind, which means it's possible to
define an `Endpoint` of any type `A` as long as there is a type-class `EncodeResponse[A]` available
for that type. Needless to say, it's pretty much straightforward to a _blocking_ instance of
`EncodeResponse[File]` that turns a given `File` into a `Buf`. Although, it might be tricky to come
up with _non-blocking_ way of serving a static content with Finch. The cornerstone idea is to return
a `Buf` instance from the endpoint so we could use an identity instance of `EncodeResponse`
type-class, thereby lifting the encoding part onto endpoint itself (where it's quite legal to return
a `Future[Buf]`).

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
import io.circe.Json

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
