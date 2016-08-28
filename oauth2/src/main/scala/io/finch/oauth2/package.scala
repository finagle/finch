package io.finch

import com.twitter.finagle.OAuth2
import com.twitter.finagle.http.Status
import com.twitter.finagle.oauth2.{AuthInfo, DataHandler, GrantHandlerResult, OAuthError}
import io.catbird.util.Rerunnable

package object oauth2 {

  private[this] val handleOAuthError: PartialFunction[Throwable, Output[Nothing]] = {
    case e: OAuthError =>
      val bearer = Seq("error=\"" + e.errorType + "\"") ++
        (if (!e.description.isEmpty) Seq("error_description=\"" + e.description + "\"") else Nil)

      Output.failure(e, Status(e.statusCode))
        .withHeader("WWW-Authenticate" -> s"Bearer ${bearer.mkString(", ")}")
  }

  /**
   * An [[Endpoint]] that takes a request (with access token) and authorizes it with respect to a
   * given `dataHandler`.
   */
  def authorize[U](dataHandler: DataHandler[U]): Endpoint[AuthInfo[U]] =
    Endpoint.embed(items.MultipleItems)(i =>
      Some(i -> new Rerunnable[Output[AuthInfo[U]]] {
        override def run = OAuth2.authorize(i.request, dataHandler).map(Output.payload(_))
      })
    ).handle(handleOAuthError)

  /**
   * An [[Endpoint]] that takes a request (with user credentials) and issues an access token for it
   * with respect to a given `dataHandler`.
   */
  def issueAccessToken[U](dataHandler: DataHandler[U]): Endpoint[GrantHandlerResult] =
    Endpoint.embed(items.MultipleItems)(i =>
      Some(i -> new Rerunnable[Output[GrantHandlerResult]] {
        override def run = OAuth2.issueAccessToken(i.request, dataHandler).map(Output.payload(_))
      })
    ).handle(handleOAuthError)
}
