## JSON

* [Argonaut](json.md#argonaut)
* [Jawn](json.md#jawn)
* [Jackson](json.md#jackson)
* [Json4s](json.md#json4s)

--

### Argonaut

The `finch-argonaut` module provides the support for the [Argonaut][1] JSON library.

### Jawn

This library is deprecated in favor of other JSON libraries.

The `finch-jawn` module provides the support for [Jawn][2].

To decode a string with Jawn, you need to import `io.finch.jawn._` and define any Jawn `Facade` as an `implicit val`.
Using either `RequiredJsonBody` or `OptionalJsonBody` will take care of the rest.

To encode a value from the Jawn AST (specifically a `JValue`), you need only import `io.finch.jawn._` and
the `Encoder` will be imported implicitly.

### Jackson

The `finch-jackson` module provides the support for the [Jackson][3] library. The library usage looks as follows:

```scala
import io.finch.jackson._
implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
case class Foo(id: Int, s: String)

val ok: HttpResponse = Ok(Foo(10, "foo")) // will be encoded as JSON
val foo: RequestReader[Foo] = body.as[Foo] // a request reader that reads Foo
```

### Json4s

The `finch-json4s` module provides the support for the [JSON4S][4] library. The library usage looks as follows:

```scala
import io.finch.json4s._
implicit val formats = DefaultFormats ++ JodaTimeSerializers.all
case class Bar(x: Int, y: Boolean)

val ok: HttpResponse = Ok(Bar(1, true)) // will be encoded as JSON
val foo: RequestReader[Bar] = body.as[Bar] // a request reader that reads Bar
```

[1]: http://argonaut.io
[2]: https://github.com/non/jawn
[3]: http://jackson.codehaus.org/
[4]: http://json4s.org/
