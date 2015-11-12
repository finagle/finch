package io.finch

import com.twitter.finagle.http.Response
import io.finch.response.EncodeResponse

/**
 * Represents a conversion from `A` to [[Response]].
 */
trait ToResponse[-A] {
  def apply(a: A): Response
}

object ToResponse {

  implicit val responseToResponse: ToResponse[Response] = new ToResponse[Response] {
    def apply(a: Response): Response = a
  }

  implicit def encodeableToResponse[A](implicit e: EncodeResponse[A]): ToResponse[A] =
    new ToResponse[A] {
      override def apply(a: A): Response = {
        val rep = Response()
        rep.content = e(a)
        rep.contentType = e.contentType
        e.charset.foreach { cs => rep.charset = cs }

        rep
      }
    }
}
