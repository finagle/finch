## Routes

* [Overview](route.md#overview)
* [Built-in Routers](route.md#built-in-routers)
* [Composing Routers](route.md#composing-routers)
* [Endpoints](route.md#endpoints)
* [Filters and Endpoints](route.md#filters-and-endpoints)

--

### Overview

The `io.finch.route` package provides _combinators_ API for building both _matching_ and _extracting_ routers in
Finch. There are two main abstractions in the package: `Route0` (matching router) and `RouteN[A]` (extracting router).
Luckily the compile figures out the exact type of the router, so you usually don't have to deal with that names.
Although, since the `RouterN[A]` class is the one that you will likely use in the Finch applications, there is a special
user-friendly type alias `Router[A]`.

At the high level, `Router[A]` might be treated as function `Route => Option[(Route, A)]`. It take an HTTP route, which
is a pair of HTTP method and HTTP path, and returns an `Option` of both the remaining route and the extracted value. A
matching `Router0` returns just `Option[Route]` if it's able to match the given route.

### Build-in Routers

There are plenty of predefined routers that match the simple part of the route or extract some value from it. You can
get of all them by importing `io.finch.route._`. 

All the matchers are available via implicit conversions from strings, integers and booleans. The following code
illustrates this functionality:

```scala
val router: Router0 = "users" // matches the current part of path
```

There is also an important type of matchers that match the HTTP method of the route. Finch supports _all_ the HTTP
methods via matchers with the corresponding names: `Post`, `Get`, `Patch`, etc.

```scala
val router = Put // matches the HTTP method
```

Finally, `*` router always matches the current part of the given route.

Things are getting interesting with extractors, i.e, `Router[N]`s. There are just four base extractors available for 
integer, string, boolean and long values.

```scala
val s: Router[String] = string
val l: Router[Long] = long
```

By default, extractors named be their types, i.e., `"string"`, `"boolean"`, etc. But you can specify the custom name for
the extractor by calling the `apply` method on it. In the example below, the string representation of the router `b` is
`":flag"`.

```scala
val b: Router[Boolean] = boolean("flag")
```

### Composing Routers

It's time to catch the beauty of route combinators API by composing the complex routers out of the tiny routers we've
seen before. There are just three operators you will need to deal with: 

* `/` or `andThen` that sequentially composes two routers together
* `|` or `orElse` that composes two routers together in terms of boolean `or`
* `/>` or `map` that maps routers to the given function

Here is an example of router that matches a route `(GET|HEAD) /users/:id` and extracts an integer value `id` from the
it.

```scala
val router: Router[Int] => (Get | Head) / "users" / int(":id")
```

The `/` case class may be used in case of complex routers that extracts several values. In the following example, we
define a `router` that extracts two integer values (i.e., `Int / Int`) out of the route and maps (using the `/>`
operator) it to `Ticket`. 
  
```scala
def getUserTicket(userId: Int, ticketId: Int): Ticket = ???
val router: Router[Int / Int] = Get / "users" / int("userId") / "tickets" / int("ticketId")
val tickets: Router[Ticket] = router /> { case userId / ticketId => getUserTicket(userId, ticketId) }
```

### Endpoints

A router that extracts `Service[Req, Rep]` out of the route is called an `Endpoint[Req, Rep]`. In, fact it's just a type
alias `type Endpoint[-A, +B] = Router[Service[A, B]]`, which brings the endpoints to the insane composability level.
This gives us an ability to compose several endpoints together with `|` or `orElse` operator. The usual practice is to
group endpoints by resource they work with.
   
```scala
val users: Endpoint[HttpRequest, HttpResponse] =
  (Get / "users" / long /> GetUser) |
  (Post / "users" /> PostUser) |
  (Get / "users" /> GetAllUsers)
```

Endpoints are implicitly convertible to Finagle `Service`s if there is an implicit view `Req => HttpRequest` available
in the scope. This means, that you can usually pass an endpoint into a `Httpx.serve` method call.

```scala
val endpoint: Endpoint[HttpRequest, HttpResponse] = users | tickets
Httpx.serve(":8081", endpoint)
```

### Filters and Endpoints

It's hard two imagine a Finagle/Finch application with out `Filter`s. While, they are not really applicable to routers,
you can always convert `Router` to `Service` and then apply any set of filters. Thus, the common practice is to have
a joint endpoint converted into service as shown bellow.
 
```scala
val api: Service[HttpRequest, HttpResponse] = users | tickets
val backend: Service[HttpRequest, HttpResponse] = handleExceptions andThen doOtherAwesomeThings andThen api
```

**Important**: Please, note that route combinators along with `io.finch.route.Endpoint` type were introduced in 0.5.0
version. There is a conflicting class `io.finch.Endpoint` you have to be aware of. The old-style endpoint is going to
be deprecated in 0.6.0. So, it's totally recommended to use route combinators instead and mask the old-style endpoint
by `import io.finch.{Endpoint => _, _}`.

--
Read Next: [Endpoints](endpoint.md)