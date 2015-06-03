/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 * Ryan Plessner
 * Pedro Viegas
 */

package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.path.Path
import com.twitter.finagle.httpx.Status
import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8
import com.twitter.util.Future

/**
 * This package enables a reasonable approach of building HTTP responses using the
 * [[io.finch.response.ResponseBuilder ResponseBuilder]] abstraction. The `ResponseBuilder` provides an immutable way
 * of building concrete [[com.twitter.finagle.httpx.Response Response]] instances by specifying their ''status'',
 * ''headers'' and ''cookies''. There are plenty of predefined builders named by HTTP statuses, i.e., `Ok`, `Created`,
 * `NotFound`. Thus, the typical use case of the `ResponseBuilder` abstraction involves usage of concrete builder
 * instead of abstract `ResponseBuilder` itself.
 *
 * {{{
 *   val ok: HttpResponse = Ok("Hello, World!")
 * }}}
 *
 * In addition to `text/plain` responses, the `ResponseBuilder` is able to build any response, whose `content-type` is
 * specified by an implicit type-class [[io.finch.response.EncodeResponse EncodeResponse]] instance. In fact, any type
 * `A` may be passed to a `RequestReader` if there is a corresponding `EncodeRequest[A]` instance available in the
 * scope.
 *
 * {{{
 *   implicit val encodeBigInt = EncodeResponse[BigInt]("text/plain") { _.toString }
 *   val ok: HttpResponse = Ok(BigInt(100))
 * }}}
 */
package object response {

  // 1xx
  object Continue extends ResponseBuilder(Status.Continue)                       // 100
  object Processing extends ResponseBuilder(Status.Processing)                   // 102

  // 2xx
  object Ok extends ResponseBuilder(Status.Ok)                                   // 200
  object Created extends ResponseBuilder(Status.Created)                         // 201
  object Accepted extends ResponseBuilder(Status.Accepted)                       // 202
  object NonAuthoritativeInformation
    extends ResponseBuilder(Status.NonAuthoritativeInformation)                  // 203
  object NoContent extends ResponseBuilder(Status.NoContent)                     // 204
  object ResetContent extends ResponseBuilder(Status.ResetContent)               // 205
  object PartialContent extends ResponseBuilder(Status.PartialContent)           // 206
  object MultiStatus extends ResponseBuilder(Status.MultiStatus)                 // 208

  // 3xx
  object MultipleChoices extends ResponseBuilder(Status.MultipleChoices)         // 300
  object MovedPermanently extends ResponseBuilder(Status.MovedPermanently)       // 301
  object Found extends ResponseBuilder(Status.Found)                             // 302
  object SeeOther extends ResponseBuilder(Status.SeeOther)                       // 303
  object NotModified extends ResponseBuilder(Status.NotModified)                 // 304
  object UseProxy extends ResponseBuilder(Status.UseProxy)                       // 305
  object TemporaryRedirect extends ResponseBuilder(Status.TemporaryRedirect)     // 307

  // 4xx
  object BadRequest extends ResponseBuilder(Status.BadRequest)                   // 400
  object Unauthorized extends ResponseBuilder(Status.Unauthorized)               // 401
  object PaymentRequired extends ResponseBuilder(Status.PaymentRequired)         // 402
  object Forbidden extends ResponseBuilder(Status.Forbidden)                     // 403
  object NotFound extends ResponseBuilder(Status.NotFound)                       // 404
  object MethodNotAllowed extends ResponseBuilder(Status.MethodNotAllowed)       // 405
  object NotAcceptable extends ResponseBuilder(Status.NotAcceptable)             // 406
  object ProxyAuthenticationRequired
    extends ResponseBuilder(Status.ProxyAuthenticationRequired)                  // 407
  object RequestTimeOut extends ResponseBuilder(Status.RequestTimeout)           // 408
  object Conflict extends ResponseBuilder(Status.Conflict)                       // 409
  object Gone extends ResponseBuilder(Status.Gone)                               // 410
  object LengthRequired extends ResponseBuilder(Status.LengthRequired)           // 411
  object PreconditionFailed extends ResponseBuilder(Status.PreconditionFailed)   // 412
  object RequestEntityTooLarge
    extends ResponseBuilder(Status.RequestEntityTooLarge)                        // 413
  object RequestUriTooLong extends ResponseBuilder(Status.RequestURITooLong)     // 414
  object UnsupportedMediaType
    extends ResponseBuilder(Status.UnsupportedMediaType)                         // 415
  object RequestedRangeNotSatisfiable
    extends ResponseBuilder(Status.RequestedRangeNotSatisfiable)                 // 416
  object ExpectationFailed extends ResponseBuilder(Status.ExpectationFailed)     // 417
  object UnprocessableEntity extends ResponseBuilder(Status.UnprocessableEntity) // 422
  object Locked extends ResponseBuilder(Status.Locked)                           // 423
  object FailedDependency extends ResponseBuilder(Status.FailedDependency)       // 424
  object UnorderedCollection extends ResponseBuilder(Status.UnorderedCollection) // 425
  object UpgradeRequired extends ResponseBuilder(Status.UpgradeRequired)         // 426
  object PreconditionRequired extends ResponseBuilder(Status(428))               // 428
  object TooManyRequests extends ResponseBuilder(Status(429))                    // 429

  // 5xx
  object InternalServerError extends ResponseBuilder(Status.InternalServerError) // 500
  object NotImplemented extends ResponseBuilder(Status.NotImplemented)           // 501
  object BadGateway extends ResponseBuilder(Status.BadGateway)                   // 502
  object ServiceUnavailable extends ResponseBuilder(Status.ServiceUnavailable)   // 503
  object GatewayTimeout extends ResponseBuilder(Status.GatewayTimeout)           // 504
  object HttpVersionNotSupported
    extends ResponseBuilder(Status.HttpVersionNotSupported)                      // 505
  object VariantAlsoNegotiates
    extends ResponseBuilder(Status.VariantAlsoNegotiates)                        // 506
  object InsufficientStorage extends ResponseBuilder(Status.InsufficientStorage) // 507
  object NotExtended extends ResponseBuilder(Status.NotExtended)                 // 510

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
    def apply(url: String): Service[HttpRequest, HttpResponse] = new Service[HttpRequest, HttpResponse] {
      override def apply(req: HttpRequest) = SeeOther.withHeaders(("Location", url))().toFuture
    }

    /**
     * Create a Service to generate redirects to the given Path.
     *
     * @param path The Finagle Path to redirect to
     *
     * @return A Service that generates a redirect to the given path
     */
    def apply(path: Path): Service[HttpRequest, HttpResponse] = this(path.toString)
  }

  /**
   * An abstraction that is responsible for encoding the response of type `A`.
   */
  trait EncodeResponse[-A] {
    def apply(rep: A): Buf
    def contentType: String
    def charset: Option[String] = Some("utf-8")
  }

  object EncodeResponse {
    /**
     * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances.
     */
    def apply[A](ct: String, cs: Option[String] = Some("utf-8"))(fn: A => Buf): EncodeResponse[A] =
      new EncodeResponse[A] {
        override def apply(rep: A): Buf = fn(rep)
        override def contentType: String = ct
        override def charset: Option[String] = cs
      }

    /**
     * Convenience method for creating new [[io.finch.response.EncodeResponse EncodeResponse]] instances
     * that treat String contents.
     */
    def fromString[A](ct: String, cs: Option[String] = Some("utf-8"))(fn: A => String): EncodeResponse[A] =
      apply(ct, cs)(fn andThen Utf8.apply)
  }

  /**
   * An abstraction that is responsible for encoding the response of a generic type.
   */
  trait EncodeAnyResponse {
    def apply[A](rep: A): Buf
    def contentType: String
  }

  /**
   * Converts [[io.finch.response.EncodeAnyResponse EncodeAnyResponse]] into
   * [[io.finch.response.EncodeResponse EncodeResponse]].
   */
  implicit def anyToConcreteEncode[A](implicit e: EncodeAnyResponse): EncodeResponse[A] =
    new EncodeResponse[A] {
      def apply(rep: A): Buf = e(rep)
      def contentType: String = e.contentType
    }

  class TurnIntoHttp[A](val e: EncodeResponse[A]) extends Service[A, HttpResponse] {
    def apply(req: A): Future[HttpResponse] = Ok(req)(e).toFuture
  }

  /**
   * A service that converts an encoded object into HTTP response with status ''OK'' using an implicit
   * [[io.finch.response.EncodeResponse EncodeResponse]].
   */
  object TurnIntoHttp {
    def apply[A](implicit e: EncodeResponse[A]): Service[A, HttpResponse] = new TurnIntoHttp[A](e)
  }

  /**
   * Allows to pass raw strings to a [[ResponseBuilder]].
   */
  implicit val encodeString: EncodeResponse[String] = EncodeResponse.fromString[String]("text/plain")(identity)

  /**
   * Allows to pass `Buf` to a [[ResponseBuilder]].
   */
  implicit val encodeBuf: EncodeResponse[Buf] =
    EncodeResponse("application/octet-stream", None)(identity)
}
