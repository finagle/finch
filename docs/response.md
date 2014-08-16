Building HTTP responses
-----------------------

An entry point into the construction of HTTP responses in **Finch.io** is the `io.finch.response.Respond` class. It supports building of three types of responses: 
* `application/json` within JSON object in the response body
* `plain/text` within string in the response body
* empty response of given HTTP status

The common practice of using the `Respond` class is following:

```scala
import io.finch.json._
import io.finch.response._

val a = Respond(Status.Ok)() // an empty response with status 200
val b = Respond(Status.NotFound)("body") // 'plain/text' response with status 404
val c = Respond(Status.Created)(JsonObject("id" -> 42)) // 'application/json' response with status 201
```

HTTP headers may be added to respond instance with `withHeaders()` method:

```scala
val ok: Respond = Ok.withHeaders("Some-Header-A" -> "a", "Some-Header-B" -> "b")
val rep: HttpResponse = ok(JsonObject("a" -> 10))
```

There are also ten predefined factory objects for the [most popular HTTP statuses](http://www.restapitutorial.com/httpstatuscodes.html):

* `Ok`
* `Created`
* `NoContent`
* `MovedPermanently`
* `SeeOther`
* `NotModified`
* `TemporaryRedirect`
* `BadRequest`
* `Unauthorized`
* `PaymentRequired`
* `Forbidden`
* `NotFound`
* `MethodNotAllowed`
* `NotAcceptable`
* `RequestTimeOut`
* `Conflict`
* `PreconditionFailed`
* `TooManyRequests`
* `InternalServerError`
* `NotImplemented`

They may be used as follows:

```scala
import io.finch._
import io.finch.request._
import io.finch.response._

object Hello extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    name <- RequiredParam("name")(req)
  } yield Ok(s"Hello, $name!")
}
```

### Redirects

There is a tiny factory object `io.finch.response.Redirect` that may be used for generation redirect services. 
Here is the example:
 
```scala
val e = new Endpoint[HttpRequest, HttpResponse] = {
  def route = {
    case Method.Get -> Root / "users" / name => GetUser(name)
    case Method.Get -> Root / "Bob" => Redirect("/users/Bob") // or with path object
  }
}
```
