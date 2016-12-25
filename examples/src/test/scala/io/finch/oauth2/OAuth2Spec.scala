package io.finch.oauth2

import com.twitter.finagle.http.Status
import io.finch.Input
import org.scalatest.{FlatSpec, Matchers}

class OAuth2Spec extends FlatSpec with Matchers {
  import Main._

  behavior of "the token-generating endpoint"
  it should "give an access token with the password grant type" in {
    val input = Input.post("/users/auth")
      .withForm(
        "grant_type" -> "password",
        "username" -> "user_name",
        "password" -> "user_password",
        "client_id" -> "user_id")

    tokens(input).awaitValueUnsafe().map(_.tokenType) shouldBe Some("Bearer")
  }

  it should "give an access token with the client credentials grant type" in {
    val input = Input.post("/users/auth")
      .withForm("grant_type" -> "client_credentials")
      .withHeaders("Authorization" -> "Basic dXNlcl9pZDp1c2VyX3NlY3JldA==")

    tokens(input).awaitValueUnsafe().map(_.tokenType) shouldBe Some("Bearer")
  }

  it should "give an access token with the auth code grant type" in {
    val input = Input.post("/users/auth")
      .withForm(
        "grant_type" -> "authorization_code",
        "code" -> "user_auth_code",
        "client_id" -> "user_id")

    tokens(input).awaitValueUnsafe().map(_.tokenType) shouldBe Some("Bearer")
  }

  it should "give back bad request if we omit the password for the password grant type" in {
    val input = Input.post("/users/auth")
      .withForm(
        "grant_type" -> "password",
        "username" -> "user_name",
        "client_id" -> "user_id")

    tokens(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.BadRequest)
  }

  it should "give back nothing for other verbs" in {
    val input = Input.get("/users/auth")
      .withForm("grant_type" -> "authorization_code", "code" -> "code", "client_id" -> "id")

    tokens(input).awaitValueUnsafe() shouldBe None
  }

  behavior of "the authorized endpoint"

  it should "work if the access token is a valid one" in {
    val input = Input.post("/users/auth")
      .withForm("grant_type" -> "client_credentials")
      .withHeaders("Authorization" -> "Basic dXNlcl9pZDp1c2VyX3NlY3JldA==")

    val authdUser = tokens(input).awaitValueUnsafe()
      .map(_.accessToken).flatMap(t =>
        users(Input.get("/users/current").withForm("access_token" -> t)).awaitValueUnsafe()
      )

    authdUser shouldBe Some(OAuthUser("user", "John Smith"))
  }

  it should "be unauthorized when using an invalid access token" in {
    val input = Input.get("/users/current")
      .withForm("access_token" -> "at-5b0e7e3b-943f-479f-beab-7814814d0315")

    users(input).awaitOutputUnsafe().map(_.status) shouldBe Some(Status.Unauthorized)
  }

  it should "give back nothing for other verbs" in {
    val input = Input.post("/users/current")
      .withForm("access_token" -> "at-5b0e7e3b-943f-479f-beab-7814814d0315")

    users(input).awaitValueUnsafe() shouldBe None
  }

  behavior of "the unprotected users endpoint"

  it should "give back the unprotected user" in {
    unprotected(Input.get("/users/unprotected")).awaitValueUnsafe() shouldBe
      Some(UnprotectedUser("unprotected"))
  }

  it should "give back nothing for other verbs" in {
    unprotected(Input.post("/users/unprotected")).awaitValueUnsafe() shouldBe None
  }
}
