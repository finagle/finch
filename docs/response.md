## Responses

* [Response Builder](response.md#response-builder)
* [HTTP Redirects](response.md#redirects)

--

### Response Builder

An entry point into the construction of HTTP responses in Finch is the `io.finch.response.ResponseBuilder` class. It
supports building of three types of responses:

* `text/plain` within string in the response body
* empty response of given HTTP status
* a type defined by implicit instance of `EncodeResponse[A]`

The common practice is to not use the `ResponseBuilder` class directly but use the predefined response builders like
`Ok`, `SeeOther`, `NotFound` and so on.

```scala
import io.finch.argonaut._
import io.finch.response._

val a = Ok() // an empty response with status 200
val b = NotFound("body") // 'text/plain' response with status 404
val c = Created(Json.obj("id" -> 42)) // 'application/json' response with status 201
```

You might be surprised but Finch has response builders for _all_ the HTTP statuses: just import `io.finch.response._`
and start typing.

```scala
import io.finch._
import io.finch.request._
import io.finch.response._
import com.twitter.finagle.http.{Request, Response}

object Hello extends Service[Request, Response] {
  def apply(req: Request) = for {
    name <- param("name")(req)
  } yield Ok(s"Hello, $name!")
}
```

#### An `EncodeResponse` Type-Class

`ResponseBuilder` may encode any type `A` into the HTTP response body if there is an implicit instance of
`EncodeResponse[A]` type-class available in the scope. An `EncodeResponse[A]` abstraction may be treated as a function
`A => com.twitter.io.Buf` that also defines a `content-type` value. For example, the implementation of `EncodeResponse[A]`
for the `finch-argonaut` module looks pretty straightforward.

```scala
implicit def encodeArgonaut[A](implicit encode: EncodeJson[A]): EncodeResponse[A] =
  EncodeResponse("application/json")(Utf8(encode.encode(_).nospaces))
```

For convenience to encode `String`, we have `fromString` method. With this, above sample would be:

```scala
implicit def encodeArgonaut[A](implicit encode: EncodeJson[A]): EncodeResponse[A] =
  EncodeResponse.fromString("application/json")(encode.encode(_).nospaces)
```

#### Charset

As a HTTP response's default charset which is appended to the `Content-Type` header value is `utf-8` in Finch, you might
need to set charset `None` explicitly when treating binary content. For example:

```scala
implicit def encodeMsgPack[A](implicit encode: EncodeMsgPack[A]): EncodeResponse[A] =
  EncodeResponse("application/msgpack", None)(encode.encode)
```

The charset may be overridden by `withCharset(charset: Option[String])` method on `ResponseBuilder`.

```scala
val ok: Response = Ok.withCharset(None)
```

#### Content Type

While the `content-type` is implicitly defined by the `EncodeResponse` instance it is possible to override its value
with `withContentType(contentType: Option[String])` method:

```scala
val ok: Response = Ok.withContentType("application/octet-stream")("byte string")
```

#### HTTP Headers

HTTP headers may be added to respond instance with `withHeaders` method:

```scala
val seeOther: ResponseBuilder = SeeOther.withHeaders("Some-Header-A" -> "a", "Some-Header-B" -> "b")
val rep: Response = seeOther(Json.obj("a" -> 10))
```

### Redirects

There is a tiny factory object `io.finch.response.Redirect` that may be used for generation redirect services. Here is
the example:

```scala
val e = new Endpoint[Request, Response] = {
  def route = {
    case Method.Get -> Root / "users" / name => GetUser(name)
    case Method.Get -> Root / "Bob" => Redirect("/users/Bob") // or with path object
  }
}
```

--
Read Next: [Authentication](auth.md)
