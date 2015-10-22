## Authentication

* [OAuth2](auth.md#authorization-with-oauth2)
* [Basic Auth](auth.md#basic-http-auth)

--

### Authentication with OAuth2

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is supported in Finch via
`finch-oauth2` package:

*Authorize*
```scala
import com.twitter.finagle.oauth2._
import io.finch.oauth2._

val dataHandler: DataHandler[Int] = ???
val auth: RequestReader[AuthInfo[Int]] = authorize(dataHandler)
val e: Endpoint[Int] = get("user" ? auth) { ai: AuthInfo[Int] => Ok(ai.user) }
```

*Issue Access Token*
```scala
import com.twitter.finagle.oauth2._
import io.finch.oauth2._

val token: RequestReader[GrandHandlerResult] = issueAccessToken(dataHandler)
val e: Endpoint[String] = get("token" ? token) { ghr: GrantHandlerResult =>
  Ok(ghr.accessToken)
}
```

Note that both `token` and `authorize` may throw `com.twitter.finagle.oauth2.OAuthError` that should be explicitly
[handled](endpoint.md#error-handling) and converted into a `Output.Failure`.

### Authentication with Basic HTTP

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is implemented as `BasicAuth` combinator
available in `finch-core`.

```scala
import io.finch._

val basicAuth: BasicAuth = BasicAuth("user", "password")
val e: Endpoint[String] = basicAuth(Endpoint(Ok("secret place")))
```

--
Read Next: [JSON](json.md)
