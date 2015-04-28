## Endpoints

**WARNING**: `io.finch.Endpoint` is going to be deprecated in the 0.6.0 release. So, it's gently recommended to use the
[route combinators](route.md) instead.

One of the most powerful abstractions in Finch is an `Endpoint`, which is a composable router. At the high level
it might be treated as a usual `PartialFunction` from request to service. Endpoints may be converted to Finagle services.
And more importantly they can be composed with other building blocks like filters, services or endpoints itself.

The core operator in Finch is _pipe_ (bang) `!` operator, which is like a Linux pipe exposes the data flow. Both
requests and responses may be piped via chain building blocks (filters, services or endpoints) in exact way it has been
written.

The common sense of using the Finch library is to have an `Endpoint` representing the domain. For example, the typical
use case would be to have an `Endpoint` from `OAuth2Request` (see [OAuth2 section](auth.md#authorization-with-oauth2))
to `Json` (see section [Json](json.md#finch-json)). Since, all the endpoints have the same type (i.e.,
`Endpoint[OAuth2Request, Json]`) they may be composed together into a single entry point with either `Endpoint.join()`
or `orElse` operators. The following example shows the discussed example in details:

```scala
val auth: Filter[HttpRequest, HttpResponse, OAuth2Request, HttpResponse] = ???
val users: Endpoint[OAuth2Request, Json] = ???
val groups: Endpoint[OAuth2Request, Json] = ???
val endpoint: Endpoint[OAuth2Request, HttpResponse] = (users orElse groups) ! TurnIntoHttp[Json]

// An HTTP endpoint that may be served with `Httpx`
val httpEndpoint: Endpoint[HttpRequest, HttpResponse] = auth ! endpoint
```

The best practices on what to choose for data transformation are following:

* Services should be used when the request is not required for the transformation.
* Otherwise, pure Finagle filters should be used.

An `Endpoint[Req, Rep]` may be implicitly converted into `Service[Req, Rep]` by importing the `io.finch._` object. Thus,  
the following code is correct.

```scala
val e: Endpoint[MyRequest, HttpResponse] = ???
// an endpoint `e` will be converted to service implicitly
Httpx.serve(new InetSocketAddress(8081), e)
```

--
Read Next: [Requests](request.md)
