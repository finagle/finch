package io.finch.oauth2

import com.twitter.finagle._
import com.twitter.finagle.oauth2.{AuthInfo, GrantHandlerResult}
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

/**
  * A simple example of finch-oauth2 usage
  *
  * Use the following sbt command to run the application.
  *
  * {{{
  *   $ sbt 'examples/runMain io.finch.oauth2.Main'
  * }}}
  *
  * Use the following HTTPie commands to test endpoints.
  *
  * {{{
  *   $ http POST :8081/users/auth Authorization:'OAuth dXNlcl9pZDp1c2VyX3NlY3JldA=='\
  *     grant_type==client_credentials
  *
  *   $ http POST :8081/users/auth grant_type==password username==user_name\
  *     password==user_password client_id==user_id
  *
  *   $ http POST :8081/users/auth grant_type==authorization_code code==user_auth_code client_id==user_id
  *
  *   $ http GET :8081/users/users/current access_token=='AT-5b0e7e3b-943f-479f-beab-7814814d0315'
  *
  *   $ http POST :8081/users/auth client_id==user_id grant_type==refresh_token\
  *     refresh_token=='RT-7e1bbf43-e7ba-4a8a-a38e-baf62ce3ceae'
  *
  *   $ http GET :8081/users/unprotected
  * }}}
  */
object Main extends App {

  case class UnprotectedUser(name: String)

  val users: Endpoint[OAuthUser] = get("users" :: "current" :: authorize(InMemoryDataHandler)) {
    ai: AuthInfo[OAuthUser] => Ok(ai.user)
  }

  val tokens: Endpoint[GrantHandlerResult] = post("users" :: "auth" :: issueAccessToken(InMemoryDataHandler))

  val unprotected: Endpoint[UnprotectedUser] = get("users" :: "unprotected") {
    Ok(UnprotectedUser("unprotected"))
  }

  Await.ready(Http.server.serve(":8081", (tokens :+: users :+: unprotected).toService))
}
