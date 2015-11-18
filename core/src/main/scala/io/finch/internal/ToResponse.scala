package io.finch.internal

import com.twitter.finagle.http.Response
import io.finch.EncodeResponse

/**
 * Represents a conversion from `A` to [[Response]].
 */
trait ToResponse[-A] {
  def apply(a: A): Response
}

object ToResponse {

  /**
   * Returns an instance for a given type.
   */
  def apply[A](implicit tr: ToResponse[A]): ToResponse[A] = tr

  /**
   * Constructs an instance from a function.
   */
  def instance[A](f: A => Response): ToResponse[A] = new ToResponse[A] {
    def apply(a: A): Response = f(a)
  }

  implicit val responseToResponse: ToResponse[Response] = instance(identity)

  implicit def encodeableToResponse[A](implicit e: EncodeResponse[A]): ToResponse[A] =
    instance { a =>
      val rep = Response()
      rep.content = e(a)
      rep.contentType = e.contentType
      e.charset.foreach { cs => rep.charset = cs }

      rep
    }
}
