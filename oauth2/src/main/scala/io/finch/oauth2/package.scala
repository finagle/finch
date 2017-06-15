package io.finch

import com.twitter.finagle.OAuth2
import com.twitter.finagle.http.Status
import com.twitter.finagle.oauth2.{AuthInfo, DataHandler, GrantHandlerResult, OAuthError}
import com.twitter.util.Future
import io.finch.internal._

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
    new Endpoint[AuthInfo[U]] {
      private[this] final def aux(i: Input): Future[Output[AuthInfo[U]]] =
        OAuth2
          .authorize(i.request, dataHandler)
          .map(ai => Output.payload(ai))
          .handle(handleOAuthError)

      final def apply(input: Input): Endpoint.Result[AuthInfo[U]] =
        EndpointResult.Matched(input, Rerunnable.fromFuture(aux(input)))
    }

  /**
   * An [[Endpoint]] that takes a request (with user credentials) and issues an access token for it
   * with respect to a given `dataHandler`.
   */
  def issueAccessToken[U](dataHandler: DataHandler[U]): Endpoint[GrantHandlerResult] =
    new Endpoint[GrantHandlerResult] {
      private[this] final def aux(i: Input): Future[Output[GrantHandlerResult]] =
        OAuth2
          .issueAccessToken(i.request, dataHandler)
          .map(ghr => Output.payload(ghr))
          .handle(handleOAuthError)

      final def apply(input: Input): Endpoint.Result[GrantHandlerResult] =
        EndpointResult.Matched(input, Rerunnable.fromFuture(aux(input)))
    }
}
