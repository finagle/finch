## Micros

* [Finch in Action](micro.md#finch-in-action)
* [Your REST API as a Monad](micro.md#your-rest-api-as-a-monad)
* [Micro](micro.md#micro)
* [Endpoint](micro.md#endpoint)
* [Custom Request Type](micro.md#custom-request-type)

--

### Finch in Action

The ["Finch in Action"][1] problem is about using types that matter rather than dealing with raw HTTP types directly.
Before version 0.6.0, it wasn't possible to avoid HTTP types (i.e., `HttpRequest`, `HttpResponse`) in the code, since
typical use-case was to create a Finch `RequestReader[A]` and call it from `Service[HttpRequest, HttpResponse]`, like
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
setting this function may be viewed as shown bellow, where transformation `1` is request decoding (deserialization),
transformation `2` - business logic and transformation `3` - response encoding (serialization).

<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/docs/req-a-b-rep.png" />
</p>

The only interesting part here is transformation `2` (i.e., `A => B`), since both `1` and `3` is just a boilerplate code
that should be provided by library. Although, it's usually challenge to deal with pure functions in a real-world
problems, so we may consider wrapping this into a monad `M[_]`, such that a transformation (i.e, `map`, `flatMap`)
`M[A] => M[B]` is a business logic of a REST API server.

Considering that function `param: String => M[Int]` creates an instance of `M[Int]` with HTTP param wrapped it, the
problem of summing up two numbers may be rewritten as following.

```scala
val sum: M[Int] = param("a") ~ param("b") map {_ + _}
```

The `~` operator from above is an applicative that takes `M[A]` and `M[B]` and produces `M[A ~ B]`.

### Micro

The `io.finch.micro` introduces a higher-kinded type `Micro[_]` that implements a monad from the previous section. In
fact, `Micro` is just an alias for `RequestReader` that does all the magic (i.e., converting the `HttpRequest` into an
arbitrary type `A`).

The example bellow defines a new `Micro[Int]` that is a maximum of two given params "a" and "b". This implies the first
stage from the previous section (i.e., request decoding).

```scala
val getSum: Micro[Int] = param("a").as[Int] ~ param("b").as[Int] ~> sum
```

### Endpoint

An `Endpoint` (`io.finch.micro.Endpoint`) is a `Router` that fetches a `Micro[HttpResponse]` from the request. Thus,
any endpoint may be implicitly converted into a Finagle service. In fact, any `Router[Micro[A]]` may be implicitly
converted into `Endpoint` if there is an implicit value of type `EncodeResponse[A]` available in the scope. So, it
implies the third stage of the request lifecycle from the previous section (i.e, response encoding). In the example
bellow, `Router[Micro[String]]` will be implicitly converted into `Endpoint` since there is an implicit value of type
`EncodeResponse[String]` provided by `io.finch.request` package.

```scala
val e: Endpoint = Get / "a" /> Micro.value("foo")
Httpx.serve(":8081", e)
```

### Custom Request Type

TODO:

--
Read Next: [Routes](route.md)

[0]: http://monkey.org/~marius/funsrv.pdf
[1]: https://gist.github.com/vkostyukov/e0e952c28b87563b2383
[2]: https://twitter.com/ID_AA_Carmack/status/53512300451201024