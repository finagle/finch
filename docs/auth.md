## Authentication

* [OAuth2](auth.md#authorization-with-oauth2)
* [Basic Auth](auth.md#basic-http-auth)

--

### Authorization with OAuth2

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is 100% compatible with
Finch.

### Basic HTTP Auth

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is implemented in the `finch-auth` module as
`finch.io.auth.BasicallyAuthorize` filter.

```scala
object ProtectedEndpoint extends Endpoint[Request, Response] {
  def route = {
    case Method.Get -> Root / "users" => BasicallyAuthorize("user", "password") ! GetUsers
  }
}
```

--
Read Next: [JSON](json.md)