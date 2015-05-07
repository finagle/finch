## JSON

* [Finch Json](json.md#finch-json)
* [Argonaut](json.md#argonaut)
* [Jawn](json.md#jawn)

--

### Finch-JSON

This library is deprecated in favor of other JSON libraries.

The Finch library is shipped with an immutable JSON API: `finch-json`, an extremely lightweight and simple
implementation of JSON: [Json.scala][3]. The usage looks as follows:

```scala
import io.finch.json._
import io.finch.request._

vaj j: Json = Json.obj("a" -> 10)
val i: RequestReader[Json] = body.as[Json]
val o: HttpResponse = Ok(Json.arr("a", "b", "c"))
```

### Argonaut

The `finch-argonaut` module provides the support for the [Argonaut][4] JSON library.

### Jawn

The `finch-jawn` module provides the support for [Jawn][5].

To decode a string with Jawn, you need to import `io.finch.jawn._` and define any Jawn `Facade` as an `implicit val`.
Using either `RequiredJsonBody` or `OptionalJsonBody` will take care of the rest.

To encode a value from the Jawn AST (specifically a `JValue`), you need only import `io.finch.jawn._` and
the `Encoder` will be imported implicitly.

### Jackson

The `finch-jackson` module provides the support for the [Jackson][6] library. The library usage looks as follows:

```scala
import io.finch.jackson._
implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
case class Foo(id: Int, s: String)

val ok: HttpResponse = Ok(Foo(10, "foo")) // will be encoded as JSON
val foo: RequestReader[Foo] = body.as[Foo] // a request reader that reads Foo
```

### Json4s

The `finch-json4s` module provides the support for the [JSON4S][7] library. The library usage looks as follows:

```scala
import io.finch.json4s._
implicit val formats = DefaultFormats ++ JodaTimeSerializers.all
case class Bar(x: Int, y: Boolean)

val ok: HttpResponse = Ok(Bar(1, true)) // will be encoded as JSON
val foo: RequestReader[Bar] = body.as[Bar] // a request reader that reads Bar
```

[3]: https://github.com/finagle/finch/blob/master/json/src/main/scala/io/finch/json/Json.scala
[4]: http://argonaut.io
[5]: https://github.com/non/jawn
[6]: http://jackson.codehaus.org/
[7]: http://json4s.org/
