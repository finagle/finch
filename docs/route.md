## Routes

* [Overview](route.md#overview)
* [Built-in Routers](route.md#built-in-routers)
  * [Matchers](route.md#matchers)
  * [Extractors](route.md#extractors)
* [Composing Routers](route.md#composing-routers)
* [Endpoints](route.md#endpoints)
* [Coproduct Routers](route.md#coproduct-routers)
* [Filters and Endpoints](route.md#filters-and-endpoints)

--

### Overview

The `io.finch.route` package provides _combinators_ API for building HTTP routers in Finch.

At the high level, `Router[A]` might be treated as a function `Route => Option[(Route, A)]`. It takes an HTTP route,
which is a pair of HTTP method and HTTP path, and returns an `Option` of both the remaining route and the extracted value.
Although, there is a special case of the `Router[A]` called `Matcher`, which doesn't extract any value from input but
matches it. So `Matcher` it's viewed as a function `Route => Option[Route]`. In fact, `Matcher` is just a case of
`Router[HNil]`, where `HNil` is an empty heterogeneous list from [Shapeless][0].

### Built-in Routers

There are plenty of predefined routers that match the simple part of the route or extract some value from it. You can
get of all them by importing `io.finch.route._`. 

#### Matchers

All the matchers are available via implicit conversions from strings, integers and booleans. The following code
illustrates this functionality:

```scala
val router: Matcher = "users" // matches the current part of path
```

Note that in the example above, `Matcher` may be safely substituted with `Router[HNil]`.

There is also an important type of matchers that match the HTTP method of the route. Finch supports _all_ the HTTP
methods via matchers with the corresponding names: `Post`, `Get`, `Patch`, etc.

```scala
val router: Matcher = Put // matches the HTTP method
```

Finally, there two special routers `*` and `**`. The `*` router always matches the current part of the given route,
while the `**` router always matches the whole route. Using both `*` and `**` routers you can build something like
fan-out proxy for the underlying services. In the example above, we redirect all the requests (with any method) like
`/users` to the `usersBackend` and requests like `/tickets` to the `ticketsBackend`.

```scala
val proxy = 
  (* / "users" / ** /> usersBackend) |
  (* / "tickets" / ** /> ticketsBackend)

Httpx.serve(":8081", proxy)
```
#### Extractors

Things are getting interesting with extractors, i.e, `Router[A]`s. There are just four base extractors available for
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

* `/` or `andThen` that sequentially composes two routers into a `Router[L <: HList]` (see [Shapeless' HList][1])
* `|` or `orElse` that composes two routers of the same type in terms of boolean `or`
* `:+:` that composes two routers of different types in terms of boolean `or`
* `/>` or `map` that maps routers to the given function

Here is an example of router that matches a route `(GET|HEAD) /users/:id/tickets/:id` and extracts two integer values
`userId` and `ticketId` from it.

```scala
val router: Router[Int :: Int :: HNil] =>
  (Get | Head) / "users" / int("userId") / "tickets" / int("ticketId")
```

No matter what are the types of left-hand/right-hand routers (`HList`-based router or value router) when applied to `/`
compositor, the correctly constructed `HList` will be yielded as a result.

```scala
def foo(i: Int, s: String): Foo = ???
val router: Router[Int :: String :: HNil] =
  (Get: Router[HNil]) / ("foo": Router[HNil]) / (int: Router[Int]) / (string: Router[String])
val fooRouter: Router[Foo] = router /> foo
```

The `|` (or `orElse`) operator composes two routers in a _greedy_ manner. If both routers are able to match
the given route, the router chosen by the `orElse` operator is that which consumes more route tokens. In the example
above, the `GET /users/100` request will be routed to the `GetUser` service since in this case, the route (i.e. path)
matched by the second router (`"GET /users/100"`) is longer than the matched route of the first router (`"GET /users"`).

```scala
val users = 
  (Get / "users" => GetAllUsers) |
  (Get / "users" / int => GetUser)
```

### Endpoints

**Important:** endpoints are deprecated in 0.7.0 (and will be removed in 0.8.0) in favour of
[Coproduct Routers](#coproduct-routers).

A router that extracts `Service[Req, Rep]` out of the route is called an `Endpoint[Req, Rep]`. In, fact it's just a type
alias `type Endpoint[-A, +B] = Router[Service[A, B]]`, which brings the endpoints to the insane composability level.
This gives us the ability to compose several endpoints together with the `|` or `orElse` operator. The usual practice is to
group endpoints by the resource that they work with.
   
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

`Service` with underlying endpoint tries to match the _full_ route and throws a `RouteNotFound` exception in case if
it's not possible. The following code shows how to handle a router exception.
  
```scala
val endpoint: Service[HttpRequest, HttpResponse] = users
endpoint(request) handle {
  case RouteNotFound(route) => NotFound(route)
}
```

### Coproduct Routers

The `|` compositor is pretty useful for the simple cases when both underlying types `A` and `B` may be substituted with
a single super type `C` such that `C >: A` and `C >: B` (usually that means that `A =:= B`). Although, it should be also
possible to compose two routers of completely different types. This is pretty doable with the `:+:` compositor that
composes two routers into a `Router[C <: Coproduct]`, where `Coproduct` is [Shapeless' disjoint union type][2].

```scala
case class Foo(i: Int)
case class Bar(s: String)

val router: Router[Foo :+: Bar :+: CNil] =
  Get / "foo" /> Foo(10) :+:
  Get / "bar" /> Bar("bar")
```

Coproduct routers are aimed to solve the problem of programming with types that actually matter rather than dealing with
HTTP types directly. That said, any coproduct router may be converted into a Finagle HTTP service (i.e.,
`Service[HttpRequest, HttpResponse]`) under the certain circumstances: every type in a coproduct should be one of the
following.

* An `HttpResponse`
* A value of a type with an `EncodeResponse` instance
* A `Future` of `HttpResponse`
* A `Future` of a value of a type with an `EncodeResponse` instance
* A `RequestReader` that returns a value of a type with an `EncodeResponse` instance
* A Finagle service that returns an `HttpResponse`
* A Finagle service that returns a value of a type with an `EncodeResponse` instance

```scala
val foo: Router[HttpResponse] = Get / "foo" /> Ok("foo")
val bar: Router[Future[String]] = Get / "bar" / "bar".toFuture
val baz: Router[RequestReader[String]] = Get / "baz" /> param("baz")

val service: Service[HttpRequest, HttpResponse] =
  (foo :+: bar :+: baz).toService
```

### Filters and Endpoints

**Important:** endpoints are deprecated in 0.7.0 (and will be removed in 0.8.0) in favour of
[Coproduct Routers](#coproduct-routers).

It's hard two imagine a Finagle/Finch application without `Filter`s. While they are not really applicable to routers,
you can always convert `Router` to `Service` and then apply any set of filters. Thus, the common practice is to have
a joint endpoint converted into service as shown below.
 
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

[0]: https://github.com/milessabin/shapeless
[1]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#heterogenous-lists
[2]: https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#coproducts-and-discriminated-unions
