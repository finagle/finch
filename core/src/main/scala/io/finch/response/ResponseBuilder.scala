package io.finch.response

import com.twitter.finagle.httpx.{Cookie, Response, Status}

/**
 * An abstraction that is responsible for building HTTP responses.
 *
 * @param status the HTTP response status
 * @param headers the HTTP headers map
 * @param cookies the HTTP cookies list
 */
@deprecated("Use Endpoint.Output instead", "0.8.5")
case class ResponseBuilder(
  status: Status,
  headers: Map[String, String] = Map.empty[String, String],
  cookies: Seq[Cookie] = Seq.empty[Cookie],
  contentType: Option[String] = None,
  charset: Option[String] = None
) {

  /**
   * Creates a new response builder with the given `headers`.
   *
   * @param headers the HTTP headers map
   */
  def withHeaders(headers: (String, String)*): ResponseBuilder = copy(headers = this.headers ++ headers)

  /**
   * Creates a new response builder with the given `cookies`.
   *
   * @param cookies the [[com.twitter.finagle.httpx.Cookie Cookie]]'s to add to the response
   */
  def withCookies(cookies: Cookie*): ResponseBuilder = copy(cookies = this.cookies ++ cookies)

  /**
   * Creates a new response builder with given `contentType`.
   *
   * @param contentType the content type to be used instead
   */
  def withContentType(contentType: Option[String]): ResponseBuilder = copy(contentType = contentType)

  /**
   * Creates a new response builder with given `charset`.
   *
   * @param charset the charset to be used instead
   */
  def withCharset(charset: Option[String]): ResponseBuilder = copy(charset = charset)

  /**
   * Builds an HTTP response of the given `body` with content-type according to the implicit
   * [[io.finch.response.EncodeResponse EncodeResponse]].
   *
   * @param body the response body
   */
  def apply[A](body: A)(implicit encode: EncodeResponse[A]): Response = {
    val rep = Response(status)
    rep.contentType = contentType.getOrElse(encode.contentType)
    charset.orElse(encode.charset).foreach { c => rep.charset = c }
    rep.content = encode(body)
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }

  /**
   * Builds an empty HTTP response.
   */
  def apply(): Response = {
    val rep = Response(status)
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }
}

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
