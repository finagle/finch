package io.finch.request

import com.twitter.finagle.httpx.Request

/**
 * A type class that provides a conversion from some type to a [[com.twitter.finagle.httpx.Request]].
 */
trait ToRequest[R] {
  def apply(r: R): Request
}

object ToRequest {
  /**
   * A convenience method that supports creating a [[ToRequest]] instance from
   * a function.
   */
  def apply[R](converter: R => Request): ToRequest[R] = new ToRequest[R] {
    def apply(r: R): Request = converter(r)
  }

  /**
   * An identity instance for [[com.twitter.finagle.httpx.Request]] itself.
   */
  implicit val requestIdentity: ToRequest[Request] = apply(identity)
}
