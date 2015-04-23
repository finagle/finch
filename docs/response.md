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

`ResponseBuilder` may encode any type `A` into the HTTP response body if there is an implicit instance of
`EncodeResponse[A]` type-class available in the scope. An `EncodeResponse[A]` abstraction may be treated as a function
`A => String` that also defines a `content-type` value. For example, the implementation of `EncodeResponse[A]` for the
`finch-argonaut` module looks pretty straightforward.

```scala
implicit def encodeArgonaut[A](implicit encode: EncodeJson[A]): EncodeResponse[A] =
  EncodeResponse("application/json")(encode.encode(_).nospaces)
```

HTTP headers may be added to respond instance with `withHeaders` method:

```scala
val seeOther: ResponseBuilder = SeeOther.withHeaders("Some-Header-A" -> "a", "Some-Header-B" -> "b")
val rep: HttpResponse = seeOther(Json.obj("a" -> 10))
```

You might be surprised but Finch has response builders for _all_ the HTTP statuses: just import `io.finch.response._`
and start typing.

```scala
import io.finch._
import io.finch.request._
import io.finch.response._

object Hello extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    name <- param("name")(req)
  } yield Ok(s"Hello, $name!")
}
```

### Redirects

There is a tiny factory object `io.finch.response.Redirect` that may be used for generation redirect services. Here is
the example:

```scala
val e = new Endpoint[HttpRequest, HttpResponse] = {
  def route = {
    case Method.Get -> Root / "users" / name => GetUser(name)
    case Method.Get -> Root / "Bob" => Redirect("/users/Bob") // or with path object
  }
}
```

--
Read Next: [Authentication](auth.md)
