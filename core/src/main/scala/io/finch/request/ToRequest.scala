package io.finch.request

import io.finch.HttpRequest

/**
 * A type class that provides a conversion from some type to a [[HttpRequest]].
 */
trait ToRequest[R] {
  def apply(r: R): HttpRequest
}

object ToRequest {
  /**
   * A convenience method that supports creating a [[ToRequest]] instance from
   * a function.
   */
  def apply[R](converter: R => HttpRequest): ToRequest[R] = new ToRequest[R] {
    def apply(r: R): HttpRequest = converter(r)
  }

  /**
   * An identity instance for [[HttpRequest]] itself.
   */
  implicit val requestIdentity: ToRequest[HttpRequest] = apply(identity)
}
