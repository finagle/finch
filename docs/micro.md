## Micros

* [Your REST API as a Monad](micro.md#your-rest-api-as-a-monad)
* [Micro](micro.md#micro)
* [Endpoint](micro.md#endpoint)

--

### Your REST API as a Monad

It's well known and widely adopted in Finagle that ["Your Server as a Function"][0] `Request => Response`. In a REST API
setting this function may be viewed as shown bellow, where stage (1) is request decoding (deserialization), stage (2) -
business logic and stage (3) - response encoding (serialization).

<p align="center">
  <img src="https://raw.githubusercontent.com/finagle/finch/master/docs/req-a-b-rep.png" />
</p>

The only interesting part here is stage (2) (i.e., `A => B`), since both (1) and (3) is just a boilerplate code that
should be provided by library. Although, it's usually challenge to deal with pure functions in a real-world problems, si
we may consider wrapping this into a monad `M[_]`, such that a transformation (i.e, `map`, `flatMap`) `M[A] => M[B]` is
a business logic of a REST API server.

### Micro

The `io.finch.micro` introduces a higher-kinded type `Micro[_]` that implements a monad from the previous section. In
fact, `Micro` is just an alias for `RequestReader` that does all the magic (i.e., converting the `HttpRequest` into an
arbitrary type `A`).

The example bellow defines a new `Micro[Int]` that is a maximum of two given params "a" and "b". This implies the first
stage from the previous section (i.e., request decoding).

```scala
val max: Micro[Int] = param("a") ~ param("b") ~> math.max
```

See ["Finch in Action"][1] for more details on `Micro` type.

### Endpoint

An `Endpoint` (`io.finch.micro.Endpoint`) is a `Router` that fetches a `Micro[HttpResponse]` from the request. Thus,
any endpoint may be implicitly converted into a Finagle service. In fact, any `Router[Micro[A]]` may be implicitly
converted into `Endpoint` if there is an implicit value of type `EncodeResponse[A]` available in the scope. So, it
implies the third stage of the request lifecycle from the previous section (i.e, response encoding). In the example
bellow, `Router[Micro[String]]` will be implicitly converted into `Endpoint` (or `Router[Micro[HttpResponse]]`) since
there is an implicit value of type `EncodeResponse[String]` provided by `io.finch.request` package.

```scala
val e: Endpoint = Get / "a" /> Micro.value("foo")
Httpx.serve(":8081", e)
```

### Custom Requests

TODO:

--
Read Next: [Routes](route.md)

[0]: http://monkey.org/~marius/funsrv.pdf
[1]: https://gist.github.com/vkostyukov/e0e952c28b87563b2383