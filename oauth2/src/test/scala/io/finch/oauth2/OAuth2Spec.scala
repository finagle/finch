package io.finch.oauth2

import com.twitter.finagle.http.{Status, Request}
import com.twitter.finagle.oauth2._
import com.twitter.util.Future
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, FlatSpec}
import org.mockito.Mockito._
import io.finch._

class OAuth2Spec extends FlatSpec with Matchers with Checkers with MockitoSugar {

  behavior of "OAuth2"

  it should "authorize the requests" in {
    val at: AccessToken = mock[AccessToken]
    val dh: DataHandler[Int] = mock[DataHandler[Int]]
    val ai: AuthInfo[Int] = mock[AuthInfo[Int]]

    when(dh.findAccessToken("bar")).thenReturn(Future.value(Some(at)))
    when(dh.isAccessTokenExpired(at)).thenReturn(false)
    when(dh.findAuthInfoByAccessToken(at)).thenReturn(Future.value(Some(ai)))
    when(ai.user).thenReturn(42)

    val authInfo: Endpoint[AuthInfo[Int]] = authorize(dh)
    val e: Endpoint[Int] = get("user" :: authInfo) { ai: AuthInfo[Int] =>
      Ok(ai.user)
    }

    val i1 = Input(Request("/user", "access_token" -> "bar"))
    val i2 = Input(Request("/user"))

    e(i1).output shouldBe Some(Ok(42))
    val Some(error) = e(i2).output
    error.status shouldBe Status.BadRequest
    error.headers should contain key "WWW-Authenticate"
  }

  it should "issue the access token" in {
    val dh: DataHandler[Int] = mock[DataHandler[Int]]
    val at: AccessToken = mock[AccessToken]

    when(at.token).thenReturn("foobar")
    when(dh.validateClient("id", "", "password")).thenReturn(Future.value(true))
    when(dh.findUser("u", "p")).thenReturn(Future.value(Some(42)))
    when(dh.getStoredAccessToken(AuthInfo(42, "id", None, None))).thenReturn(Future.value(Some(at)))
    when(dh.isAccessTokenExpired(at)).thenReturn(false)

    val grandHandlerResult: Endpoint[GrantHandlerResult] = issueAccessToken(dh)
    val e: Endpoint[String] = get("token" :: grandHandlerResult) { ghr: GrantHandlerResult =>
      Ok(ghr.accessToken)
    }

    val i1 = Input(Request("/token",
      "grant_type" -> "password", "username" -> "u", "password" -> "p", "client_id" -> "id"
    ))
    val i2 = Input(Request("/token"))

    e(i1).output shouldBe Some(Ok("foobar"))
    val Some(error) = e(i2).output
    error.status shouldBe Status.BadRequest
    error.headers should contain key "WWW-Authenticate"
  }
}
