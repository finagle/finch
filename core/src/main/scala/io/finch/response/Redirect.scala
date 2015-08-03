package io.finch.response

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Response, Request}
import com.twitter.finagle.httpx.path.Path
import io.finch._

/**
 * A factory for Redirecting to other URLs.
 */
object Redirect {
  /**
   * Create a Service to generate redirects to the given url.
   *
   * @param url The url to redirect to
   *
   * @return A Service that generates a redirect to the given url
   */
  def apply(url: String): Service[Request, Response] = new Service[Request, Response] {
    override def apply(req: Request) = SeeOther.withHeaders(("Location", url))().toFuture
  }

  /**
   * Create a Service to generate redirects to the given Path.
   *
   * @param path The Finagle Path to redirect to
   *
   * @return A Service that generates a redirect to the given path
   */
  def apply(path: Path): Service[Request, Response] = this(path.toString)
}
