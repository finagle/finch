## Authentication

* [OAuth2](auth.md#authorization-with-oauth2)
* [Basic Auth](auth.md#basic-http-auth)

--

### Authorization with OAuth2

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is 100% compatible with
Finch. There is no Finch-specific abstractions available in Finch, but this work is [in progress][1].

### Basic HTTP Auth

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is implemented as `basicAuth` combinator
available in `finch-core`. The `basicAuth` takes two param lists: 1) username and password and 2) a `Router` that has to
be authorized.

```scala
import io.finch.route._

val router: Router[String] = Router.value("42")
val authRouter: Router[String] = basicAuth("username", "password")(router)
```

--
Read Next: [JSON](json.md)

[1]: https://github.com/finagle/finch/issues/136