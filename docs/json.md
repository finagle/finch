## JSON

* [Circe](json.md#circe)
* [Argonaut](json.md#argonaut)
* [Jackson](json.md#jackson)
* [Json4s](json.md#json4s)

--

Finch uses type classes `EncodeResponse` and `DecodeRequest` to make its JSON support pluggable. Thus in most of the
cases it's not necessary to make any code changes (except for import statements) while switching the JSON backend.

Finch comes with a rich support of many modern JSON libraries. While it's totally possible to use Finch with runtime
reflection based libraries such as [Jackson][jackson], it's highly recommended to use compile-time based solutions such
as [Circe][circe] and [Argonaut][argonaut]. At start, Circe would be the best possible choice as a JSON library with
great performance and no boilerplate.

Use the following instructions to enable a support for a particular JSON library.

### Circe

* Bring the dependency to the `finch-circe` module.
* Make sure for each domain type there are implicit instances of `io.circe.Encoder[A]` and `io.circe.Decoder[A]` in the
  scope or Circe's generic auto derivation is used via `import io.circe.generic.auto_`.

```scala
import io.finch.circe._
import io.circe.generic.auto._
```

### Argonaut

* Bring the dependency to the `finch-argonaut` module.
* Make sure for each domain type there are instances of `argonaut.EncodeJson[A]` and `argonaut.DecodeJson[A]` in the
  scope.

```scala
import argonaut._
import argonaut.Argonaut._
import io.finch.argonaut._

implicit val e: EncodeJson[_] = ???
implicit val d: DecodeJson[_] = ???
```

### Jackson

* Bring the dependency to the `finch-jackson` module.
* Make sure there is an implicit instance of `com.fasterxml.jackson.databind.ObjectMapper` in the scope.

```scala
import io.finch.jackson._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
```

### Json4s

* Bring the dependency to the `finch-json4s` module.
* Make sure there is an implicit instance of `Formats` in the scope.

```scala
import io.finch.json4s._
import org.json4s.DefaultFormats

implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
```

[argonaut]: http://argonaut.io
[jackson]: http://wiki.fasterxml.com/JacksonHome
[json4s]: http://json4s.org/
[circe]: https://github.com/travisbrown/circe

--
Read Next: [Best Practices](best-practices.md)

