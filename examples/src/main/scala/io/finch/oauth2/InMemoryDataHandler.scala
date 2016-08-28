package io.finch.oauth2

import java.util.{Date, UUID}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.concurrent.duration._

import com.twitter.finagle.oauth2.{AccessToken, AuthInfo, DataHandler}
import com.twitter.util.Future

object InMemoryDataHandler extends DataHandler[OAuthUser] {
  private[this] case class AuthData(
   username: String,
   password: String,
   clientId: String,
   clientSecret: String,
   authCode: String,
   user: OAuthUser
  )

  private[this] val clients = List[AuthData](
    AuthData(
      "user_name",
      "user_password",
      "user_id",
      "user_secret",
      "user_auth_code",
      OAuthUser("user", "John Smith")
    ),
    AuthData(
      "admin_name",
      "admin_password",
      "admin_id",
      "admin_secret",
      "admin_auth_code",
      OAuthUser("admin", "Brad Johnes")
    )
  )

  private[this] val accessTokens = new ConcurrentHashMap[String, AccessToken]().asScala
  private[this] val authInfosByAccessToken = new ConcurrentHashMap[String, AuthInfo[OAuthUser]]().asScala

  private[this] def makeToken: AccessToken = {
    new AccessToken(
      token = s"AT-${UUID.randomUUID()}",
      refreshToken = Some(s"RT-${UUID.randomUUID()}"),
      scope = None,
      expiresIn = Some(1.minute.toSeconds),
      createdAt = new Date()
    )
  }

  override def validateClient(clientId: String, clientSecret: String, grantType: String): Future[Boolean] = {
    Future.value(clients.exists { case ad => ad.clientId.equals(clientId) })
  }

  override def findUser(username: String, password: String): Future[Option[OAuthUser]] = {
    clients.find { case ad => username.equals(ad.username) && password.equals(ad.password) } match {
      case Some(ad) => Future.value(Some(ad.user))
      case None => Future.value(None)
    }
  }

  override def createAccessToken(authInfo: AuthInfo[OAuthUser]): Future[AccessToken] = {
    val token = makeToken

    accessTokens += (authInfo.clientId -> token)
    authInfosByAccessToken += (token.token -> authInfo)

    Future.value(token)
  }

  override def getStoredAccessToken(authInfo: AuthInfo[OAuthUser]): Future[Option[AccessToken]] = {
    Future.value(accessTokens.get(authInfo.clientId))
  }

  override def refreshAccessToken(authInfo: AuthInfo[OAuthUser], refreshToken: String): Future[AccessToken] = {
    accessTokens.find { case (id, at) => at.refreshToken.exists(_.equals(refreshToken)) } match {
      case Some((id, _)) => accessTokens -= id
      case None => Future.exception(new IllegalArgumentException)
    }

    createAccessToken(authInfo)
  }

  override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[OAuthUser]]] = {
    clients.find { case ad => code.equals(ad.authCode) } match {
      case Some(ad) =>
        Future.value(Some(AuthInfo[OAuthUser](
          ad.user,
          ad.clientId,
          Some(ad.user.scope),
          None
        )))
      case None => Future.value(None)
    }
  }

  override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[OAuthUser]]] = {
    accessTokens.values.find { at: AccessToken => at.refreshToken.exists(_.equals(refreshToken)) } match {
      case Some(at) => Future.value(authInfosByAccessToken.get(at.token))
      case None => Future.value(None)
    }
  }

  override def findClientUser(
    clientId: String, clientSecret: String, scope: Option[String]
  ): Future[Option[OAuthUser]] = {
    clients.find { case ad => clientId.equals(ad.clientId) && clientSecret.equals(ad.clientSecret) } match {
      case Some(ad) => Future.value(Some(ad.user))
      case None => Future.value(None)
    }
  }

  override def findAccessToken(token: String): Future[Option[AccessToken]] = {
    Future.value(accessTokens.values.find { at: AccessToken => at.token.equals(token) })
  }

  override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[OAuthUser]]] = {
    Future.value(authInfosByAccessToken.get(accessToken.token))
  }
}
