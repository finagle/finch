## Authentication

* [OAuth2](auth.md#authentication-with-oauth2)
* [Basic Auth](auth.md#authentication-with-basic-http)

--

### Authentication with OAuth2

There is [finagle-oauth2](https://github.com/finagle/finagle-oauth2) server-side provider that is
supported in Finch via the `finch-oauth2` package:

*Authorize*
```scala
import com.twitter.finagle.oauth2._
import io.finch.oauth2._

val dataHandler: DataHandler[Int] = ???
val auth: Endpoint[AuthInfo[Int]] = authorize(dataHandler)
val e: Endpoint[Int] = get("user" :: auth) { ai: AuthInfo[Int] => Ok(ai.user) }
```

*Issue Access Token*
```scala
import com.twitter.finagle.oauth2._
import io.finch.oauth2._

val token: Endpoint[GrantHandlerResult] = issueAccessToken(dataHandler)
```

Note that both `token` and `authorize` may throw `com.twitter.finagle.oauth2.OAuthError`, which is
already _handled_ by a returned endpoint but needs to be serialized. This means you might want to
include its serialization logic into an instance of `EncodeResponse[Exception]`.

### Authentication with Basic HTTP

[Basic HTTP Auth](http://en.wikipedia.org/wiki/Basic_access_authentication) is implemented as the
`BasicAuth` combinator available in `finch-core`.

```scala
import io.finch._

val basicAuth: BasicAuth = BasicAuth("realm") { (user, password) =>
  user == "user" && password == "password"
}
val e: Endpoint[String] = basicAuth(Endpoint.liftOutput(Ok("secret place")))
```

--
Read Next: [JSON](json.md)
