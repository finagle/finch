Piping the Requests/Responses
-----------------------------

The core operation in Finch.io is _pipe_ `!`. Both requests and responses may be piped via chain of filters, services or endpoints: the data flows in a natural direction - from left to right.

In the following example

- the request flows to `Auth` filter and then
- the request is converted to response by `respond` endpoint and then
- the response flows to `TurnJsonIntoHttp` service

```scala
val respond: Endpoint[HttpRequest, HttpResponse] = ???
val endpoint = Auth ! respond ! TurnJsonIntoHttp
```

The best practices on what to choose for data transformation are following

* Services should be used when the request is not required for the transformation.
* Otherwise, pure filters should be used.

###### Read Next: [Requests](request.md)