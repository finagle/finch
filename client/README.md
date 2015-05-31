The `finch-client` module provides an attempt at a finch styled rest-client.

Useage
------------
```scala
val connection = FinchRequest(Connection("api.github.com"), Path("/users/penland365"))
val resource = for {
    i <- Get(rReq) // Http Verb is the function to send the request
    j <- Future.value(i.toList.head) // Get returns a String Xor Resource
    k <- Future.value(j.as[Json]) // requires a DecodeResource TypeClass
    l <- Future.value(k.getOrDefault(YourDefaultResourceHere))
  } yield l
val = Ok(headerMap, argonaut.Json)
```
