## JSON

* [Argonaut](json.md#argonaut)
* [Jackson](json.md#jackson)
* [Json4s](json.md#json4s)

--

### Argonaut

The `finch-argonaut` module provides the support for the [Argonaut][1] JSON library.

### Jackson

The `finch-jackson` module provides the support for the [Jackson][2] library. The library usage looks as follows:

```scala
import io.finch.jackson._
implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
case class Foo(id: Int, s: String)

val ok: Response = Ok(Foo(10, "foo")) // will be encoded as JSON
val foo: RequestReader[Foo] = body.as[Foo] // a request reader that reads Foo
```

### Json4s

The `finch-json4s` module provides the support for the [JSON4S][3] library. The library usage looks as follows:

```scala
import io.finch.json4s._
implicit val formats = DefaultFormats ++ JodaTimeSerializers.all
case class Bar(x: Int, y: Boolean)

val ok: Response = Ok(Bar(1, true)) // will be encoded as JSON
val foo: RequestReader[Bar] = body.as[Bar] // a request reader that reads Bar
```

[1]: http://argonaut.io
[2]: http://jackson.codehaus.org/
[3]: http://json4s.org/
