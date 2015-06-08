## Micros

* [Finch in Action](micro.md#finch-in-action)
* [Your REST API as a Monad](micro.md#your-rest-api-as-a-monad)
* [Micro](micro.md#micro)
* [Endpoint](micro.md#endpoint)
* [Custom Request Type](micro.md#custom-request-type)

--

**The `micro` package is deprecated since 0.7.0 and will be removed in 0.8.0**

### Finch in Action

The ["Finch in Action"][1] problem is about using types that matter rather than dealing with raw HTTP types directly.
Before version 0.6.0, it wasn't possible to avoid HTTP types (i.e., `HttpRequest`, `HttpResponse`) in the code, since
the typical use-case is to create a Finch `RequestReader[A]` and call it from `Service[HttpRequest, HttpResponse]`, like
this:

```scala
val sum = new Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = for {
    a <- param("a")(req)
    b <- param("b")(req)
  } yield Ok("a + b = ${a + b}")
}
```

The code above has a lot of boilerplate that makes no sense in the domain application, since the sum of two integer
numbers [is just a function][2] `(Int, Int) => Int` but not a `Service[HttpRequest, HttpResponse]`. Ideally, it should
look like:

```scala
def sum(a: Int, b: Int): Int = a + b
```

That said, Finch should be able to serve this function as an HTTP/REST service with a reasonably small amount of
boilerplate code. This might be achieved by treating a REST API server as a monad.

### Your REST API as a Monad

It's well known and widely adopted in Finagle that ["Your Server as a Function"][0] `Request => Response`. In a REST API
setting this function may be viewed as shown below, where transformation `1` is request decoding (deserialization),
transformation `2` - business logic and transformation `3` - response encoding (serialization).

<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/docs/req-a-b-rep.png" />
</p>

The only interesting part here is transformation `2` (i.e., `A => B`), since both `1` and `3` are just a boilerplate code
that should be provided by library. It's usually a challenge to deal with pure functions in a real-world
problems, so we wrap this into a monad `M[_]`, such that a transformation (i.e, `map`, `flatMap`)
`M[A] => M[B]` encodes the business logic of a REST API server.

Considering that function `param: String => M[Int]` creates an instance of `M[Int]` with HTTP param wrapped it, the
problem of summing up two numbers may be rewritten as following (using `map` and `flatMap`).

```scala
val getSum: M[Int] = for {
 a <- param("a")
 b <- param("b")
} yield sum(a, b)
```

### Micro

The `io.finch.micro` introduces a higher-kinded type `Micro[_]` that implements a monad from the previous section. In
fact, `Micro` is just an alias for `RequestReader` that does all the magic (i.e., converting the `HttpRequest` into an
arbitrary type `A`).

The example below defines a new `Micro[Int]` that sums up two given numbers passed via query-string params "a" and "b".
Note that we reuse an existing function `sum` here that actually does the work.

```scala
val getSum: Micro[Int] = param("a").as[Int] ~ param("b").as[Int] ~> sum
```

Note that `getSum` wires params reading (request decoding) and business logic (function `sum`) together. Thus, while
`sum` itself represents a second transformation from the request lifecycle, `getSum` is a first one.

### Endpoint

An `Endpoint` (`io.finch.micro.Endpoint`) is a `Router` that fetches a `Micro[HttpResponse]` from the request. Thus,
any endpoint may be implicitly converted into a Finagle service. In fact, any `Router[Micro[A]]` may be implicitly
converted into `Endpoint` if there is an implicit value of type `EncodeResponse[A]` available in the scope. So, it
implies the third transformation of the request lifecycle from the previous section (i.e, response encoding).

In the example below, `Router[Micro[String]]` is implicitly converted into `Endpoint` since there is an implicit
value of type `EncodeResponse[String]` provided by the `io.finch.request` package.

```scala
val e: Endpoint = Get / "a" /> Micro.value("foo")
Httpx.serve(":8081", e)
```

### Custom Request Type

Since version 0.6.0 it's easily possible to use `Micro`s (`RequestReader`s) to read data from a request of an arbitrary
type. In fact, a generalized version of `Micro` looks as follows.

```scala
trait PMicro[R, A] {
  def apply(req: R): Future[A]
}
```

It implies to be _polymorphic_ in terms of request type. Thus, `Micro[A]` is just an alias to `PMicro[HttpRequest, A]`.
That said, it's possible to create an instance of `Micro` that curry a custom request type. There is a factory method
in companion object that allows it.

```scala
object Micro {
  def apply[R, A](f: R => A): PMicro[R, A]
}
```

So far so good, but it's not clear yet how to compose `PMicro`s with just `Micro`s since they have different request
types (in `Micro` it's always fixed to `HttpRequest`). `PMicro` is flexible enough to allow it with one constrain. It's
possible to compose (using `flatMap` or `~`) `PMicro[R, _]` with `PMicro[S, _]` if there is an implicit view `R %> S`
available in the scope. Note, that it uses it's own version of implicit view `io.finch.request.View`, which is just a
safe alternative to a standard implementation via function.

The following example show usage of a custom request type `SumReq` that curries both values we need to sum.

```scala
case class SumReq(a: Int, b: Int, http: HttpRequest)
implicit val sumReqIsHttp: SumReq %> HttpRequest = View(_.http)

// It makes sense to create these aliases and do not write the full signature every time
type SMicro[A] = PMicro[SumReq, A]
type SEndpoint = PEndpoint[SumReq]

val getSum: SMicro[Int] = Micro[SumReq, Int](_.a) ~ Micro[SumReq, Int](_.b) ~> sum
```

There is a [playground project][3] that uses custom request type and polymorphic micros.

[0]: http://monkey.org/~marius/funsrv.pdf
[1]: https://gist.github.com/vkostyukov/e0e952c28b87563b2383
[2]: https://twitter.com/ID_AA_Carmack/status/53512300451201024
[3]: https://github.com/finagle/finch/blob/master/playground/src/main/scala/io/finch/playground/Main.scala
