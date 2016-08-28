## JSON

* [Circe](json.md#circe)
* [Argonaut](json.md#argonaut)
* [Jackson](json.md#jackson)
* [Json4s](json.md#json4s)
* [PlayJson](json.md#playjson)

--

Finch uses type classes `io.finch.Encode` and `io.finch.Deocde` to make its JSON support pluggable.
Thus in most of the cases it's not necessary to make any code changes (except for import statements)
while switching the JSON backend.

Finch comes with a rich support of many modern JSON libraries. While it's totally possible to use
Finch with runtime reflection based libraries such as [Jackson][jackson], it's highly recommended to
use compile-time based solutions such as [Circe][circe] and [Argonaut][argonaut]. When starting
out, Circe would be the best possible choice as a JSON library due to its great performance and
lack of boilerplate.

Use the following instructions to enable support for a particular JSON library.

### Circe

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
prefer the [Jackson serializer][circe-jackson] for better performance. The following two imports
show how to make Circe use Jackson while serializing instead of the built-in pretty printer.

```scala
import io.finch.circe.jacksonSerializer._
import io.circe.generic.auto._
```

### Argonaut

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

### Jackson

* Add the dependency to the `finch-jackson` module.
* Make sure there is an implicit instance of `com.fasterxml.jackson.databind.ObjectMapper` in the
  scope.

```scala
import io.finch.jackson._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)
```

### Json4s

* Add the dependency to the `finch-json4s` module.
* Make sure there is an implicit instance of `Formats` in the scope.

```scala
import io.finch.json4s._
import org.json4s.DefaultFormats

implicit val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
```

### PlayJson

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

### Spray-Json

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

[argonaut]: http://argonaut.io
[jackson]: http://wiki.fasterxml.com/JacksonHome
[json4s]: http://json4s.org/
[circe]: https://github.com/travisbrown/circe
[circe-jackson]: https://github.com/travisbrown/circe/pull/111
[playjson]: https://www.playframework.com/documentation/2.4.x/ScalaJson
[spray-json]: https://github.com/spray/spray-json

--
Read Next: [Cookbook](cookbook.md)
