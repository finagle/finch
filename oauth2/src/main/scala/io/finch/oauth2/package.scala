package io.finch

import com.twitter.finagle.http.Request
import com.twitter.finagle.OAuth2
import com.twitter.finagle.oauth2.{GrantHandlerResult, AuthInfo, DataHandler}
import com.twitter.util.Future
import io.finch.items._

package object oauth2 {

  private[this] object OAuth2 extends OAuth2

  def authorize[U](dataHandler: DataHandler[U]): RequestReader[AuthInfo[U]] =
    new RequestReader[AuthInfo[U]] {
      val item: RequestItem = MultipleItems
      def apply(req: Request): Future[AuthInfo[U]] = OAuth2.authorize(req, dataHandler)
    }

  def issueAccessToken[U](dataHandler: DataHandler[U]): RequestReader[GrantHandlerResult] =
    new RequestReader[GrantHandlerResult] {
      val item: RequestItem = MultipleItems
      def apply(req: Request): Future[GrantHandlerResult] = OAuth2.issueAccessToken(req, dataHandler)
    }
}
