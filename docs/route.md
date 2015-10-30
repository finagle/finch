## Routes

* [Overview](route.md#overview)
* [Built-in Routers](route.md#built-in-routers)
  * [Matching Routers](route.md#matching-routers)
  * [Extracting Routers](route.md#extracting-routers)
* [Composing Routers](route.md#composing-routers)
* [Endpoints](route.md#endpoints)
* [Coproduct Routers](route.md#coproduct-routers)
* [Filters and Endpoints](route.md#filters-and-endpoints)

--

### Overview

The `io.finch.route` package provides _combinators_ API for building HTTP routers in Finch.

At the high level, `Router[A]` might be treated as a function `Request => Option[A]`. It takes an HTTP request and tries
to _route_ it in the meaning of extracting some value of type `A` from the given request. `Router`s that extract nothing
is represented as `Router[HNil]`, where `HNil` is an empty heterogeneous list from [Shapeless][0].

`Router`s are able to extract several values of the different types from the request. In this case `Router` is
represented as `Router[L <: HList]`. For the sake of simplicity, there are user-friendly aliases available that allow to
avoid typing `HList` types (and imports).

```scala
type Router0 = Router[HNil]
type Router2[A, B] = Router[A :: B :: HNil]
type Router3[A, B, C] = Router[A :: B :: C :: HNil]
```

Although, the `Router[L <: HList]` usually represents an _intermediate_ result, which is then mapped (transformed) into
some meaningful type.

### Built-in Routers

There are plenty of predefined routers that match the simple part of the route or extract some value from it. You can
get of all them by importing `io.finch.route._`. 

#### Matching Routers

All the matching routers (i.e., `Router0`s) are available via implicit conversions from strings, integers and booleans.

```scala
val router: Router0 = "users" // matches the current part of path
```

Note that in the example above, `Router0` may be safely substituted with `Router[HNil]`.

Matching the HTTP methods is done in a bit different way. There are functions of type `Router[A] => Router[A]` that
take some `Router` and wrap it with an anonymous `Router` that also matches the HTTP method.

```scala
val router: Router0 = get("users")
```

Note that string  `"users"` in the example above, is implicitly converted into a `Router0`.

Finally, there two special routers `*` and `/`. The `*` router always matches the tail of the route. The `/` router
represents an _identity router_.

```scala
val r: Router0 = get(/) // matches all the GET requests
```

#### Extracting Routers

Things are getting interesting with extracting routers, i.e, `Router[A]`s. There are just four base extractors available
for integer, string, boolean and long values.

```scala
val s: Router[String] = string
val l: Router[Long] = long
```

There are also tail extracting routers available out of the box. For example, the `strings` router has type
`Router[Seq[String]]` and extracts the tail value from the path.

By default, extractors named be their types, i.e., `"string"`, `"boolean"`, etc. But you can specify the custom name for
the extractor by calling the `apply` method on it. In the example below, the string representation of the router `b` is
`":flag"`.

```scala
val b: Router[Boolean] = boolean("flag")
```

### Composing Routers

It's time to catch the beauty of route combinators API by composing the complex routers out of the tiny routers we've
seen before. There are just three operators you will need to deal with: 

* `/` that sequentially composes two routers into a `Router[L <: HList]` (see [Shapeless' HList][1])
* `|` that composes two routers of the same type in terms of boolean `or`
* `:+:` that composes two routers of different types in terms of boolean `or`

Here is an example of router that matches a route `GET /users/:id/tickets/:id` and extracts two integer values
`userId` and `ticketId` from it.

```scala
val router: Router[Int :: Int :: HNil] =>
   get("users" / int("userId") / "tickets" / int("ticketId"))
```

No matter what are the types of left-hand/right-hand routers (`HList`-based router or value router) when applied to `/`
compositor, the correctly constructed `HList` will be yielded as a result.

It's also possible to compose the `Router` with either function `A => B` or `A => Future[B]`. To do so, simple pass a
function to the `apply` method on `Router`.

```scala
val i: Router[Int] = Router.value(100)
val s: Router[String] = i { x: Int => x.toString }
val l: Router[Long] = s { x: String => Future.value(x.toLong) }
```

The only downside of this feature is that you have to always specify all the types in the functions you're passing to
the `apply` method.

Finally, it's possible to compose `Router`s with `RequestReader`s. Such composition is done by the `?` method that takes
a `Router[A]` and a `RequestReader[B]` and returns a `Router[A :: B :: HNil]`.

```scala
val r1: RequestReader[Int :: String :: HNil] = param("a").as[Int] :: param("b")
val r2: Router[Boolean] = Router.value(true)
val r3: Router[Boolean :: Int :: String :: HNil] = r2 ? r1
```

### Endpoints

**Important:** endpoints are deprecated in 0.8.0 (and will be removed in 0.9.0) in favour of
[Coproduct Routers](#coproduct-routers).

A router that extracts `Service[Req, Rep]` out of the route is called an `Endpoint[Req, Rep]`. In, fact it's just a type
alias `type Endpoint[-A, +B] = Router[Service[A, B]]`, which brings the endpoints to the insane composability level.
This gives us the ability to compose several endpoints together with the `|` or `orElse` operator. The usual practice is to
group endpoints by the resource that they work with.
   
```scala
val users: Endpoint[Request, Response] =
  (Get / "users" / long /> GetUser) |
  (Post / "users" /> PostUser) |
  (Get / "users" /> GetAllUsers)
```

Endpoints are implicitly convertible to Finagle `Service`s if there is an implicit view `Req => Request` available
in the scope. This means, that you can usually pass an endpoint into a `Http.serve` method call.

```scala
val endpoint: Endpoint[Request, Response] = users | tickets
Http.serve(":8081", endpoint)
```

`Service` with underlying endpoint tries to match the _full_ route and throws a `RouteNotFound` exception in case if
it's not possible. The following code shows how to handle a router exception.
  
```scala
val endpoint: Service[Request, Response] = users
endpoint(request) handle {
  case RouteNotFound(route) => NotFound(route)
}
```

### Coproduct Routers

The `:+:` combinator composes two routers into a `Router[C <: Coproduct]`, where `Coproduct` is
[Shapeless' disjoint union type][2].

```scala
case class Foo(i: Int)
case class Bar(s: String)

val router: Router[Foo :+: Bar :+: CNil] =
  get("foo") { Foo(10) } :+:
  get("bar") { Bar("bar") }
```

Coproduct routers are aimed to solve the problem of programming with types that actually matter rather than dealing with
HTTP types directly. That said, any coproduct router may be converted into a Finagle HTTP service (i.e.,
`Service[Request, Response]`) under the certain circumstances: every type in a coproduct should be one of the
following.

* An `Response`
* A value of a type with an `EncodeResponse` instance
* A Finagle service that returns an `Response`
* A Finagle service that returns a value of a type with an `EncodeResponse` instance

```scala
val foo: Router[Response] = get("foo") { Ok("foo") }
val bar: Router[String] = get("bar") { "bar" }

val service: Service[Request, Response] =
  (foo :+: bar).toService
```

### Filters and Endpoints

**Important:** endpoints are deprecated in 0.7.0 (and will be removed in 0.8.0) in favour of
[Coproduct Routers](#coproduct-routers).

It's hard two imagine a Finagle/Finch application without `Filter`s. While they are not really applicable to routers,
you can always convert `Router` to `Service` and then apply any set of filters. Thus, the common practice is to have
a joint endpoint converted into service as shown below.
 
```scala
val api: Service[Request, Response] = users | tickets
val backend: Service[Request, Response] = handleExceptions andThen doOtherAwesomeThings andThen api
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
