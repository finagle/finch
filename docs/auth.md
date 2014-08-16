Authorization with OAuth2
-------------------------

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is 100% compatible with **Finch.io**.

Basic HTTP Auth
---------------

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is supported out-of-the-box and implemented 
as `finch.io.auth.BasicallyAuthorize` filter.

```scala
object ProtectedEndpoint extends Endpoint[HttpRequest, HttpResponse] {
  def route = {
    case Method.Get -> Root / "users" => BasicallyAuthorize("user", "password") ! GetUsers
  }
}
```

###### Read Next: [JSON](json.md)