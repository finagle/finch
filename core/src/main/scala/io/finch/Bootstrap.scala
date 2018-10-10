package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import shapeless._

/**
 * Bootstraps a Finagle HTTP service out of the collection of Finch endpoints.
 *
 * {{{
 * val api: Service[Request, Response] = Bootstrap
 *  .configure(negotiateContentType = true, enableMethodNotAllowed = true)
 *  .serve[Application.Json](getUser :+: postUser)
 *  .serve[Text.Plain](healthcheck)
 *  .toService
 * }}}
 *
 *
 * == Supported Configuration Options ==
 *
 * - `includeDateHeader` (default: `true`): whether or not to include the Date header into
 *   each response (see RFC2616, section 14.18)
 *
 * - `includeServerHeader` (default: `true`): whether or not to include the Server header into
 *   each response (see RFC2616, section 14.38)
 *
 * - `negotiateContentType` (default: `false`): whether or not to enable server-driven content type
 *   negotiation (see RFC2616, section 12.1)
 *
 * - `enableMethodNotAllowed` (default: `false`): whether or not to enable 405 MethodNotAllowed HTTP
 *   response (see RFC2616, section 10.4.6)
 *
 * - `enableUnsupportedMediaType` (default: `false`) whether or not to enable 415
 *   UnsupportedMediaType HTTP response (see RFC7231, section 6.5.13)
 *
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec12.html
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
 * @see https://tools.ietf.org/html/rfc7231#section-6.5.13
 */
class Bootstrap[ES <: HList, CTS <: HList](
    val endpoints: ES,
    val includeDateHeader: Boolean = true,
    val includeServerHeader: Boolean = true,
    val negotiateContentType: Boolean = false,
    val enableMethodNotAllowed: Boolean = false,
    val enableUnsupportedMediaType: Boolean = false) { self =>

  class Serve[CT] {
    def apply[F[_], E](e: Endpoint[F, E]): Bootstrap[Endpoint[F, E] :: ES, CT :: CTS] =
      new Bootstrap[Endpoint[F, E] :: ES, CT :: CTS](
        e :: self.endpoints,
        includeDateHeader,
        includeServerHeader,
        negotiateContentType,
        enableMethodNotAllowed,
        enableUnsupportedMediaType
      )
    }

  def configure(
    includeDateHeader: Boolean = self.includeDateHeader,
    includeServerHeader: Boolean = self.includeServerHeader,
    negotiateContentType: Boolean = self.negotiateContentType,
    enableMethodNotAllowed: Boolean = self.enableMethodNotAllowed,
    enableUnsupportedMediaType: Boolean = self.enableUnsupportedMediaType
  ): Bootstrap[ES, CTS] = new Bootstrap[ES, CTS](
    endpoints,
    includeDateHeader,
    includeServerHeader,
    negotiateContentType,
    enableMethodNotAllowed,
    enableUnsupportedMediaType
  )

  def serve[CT]: Serve[CT] = new Serve[CT]

  def toService(implicit ts: ToService[ES, CTS]): Service[Request, Response] = {
    val opts = ToService.Options(
      includeDateHeader,
      includeServerHeader,
      negotiateContentType,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

    val ctx = ToService.Context()

    ts(endpoints, opts, ctx)
  }

  final override def toString: String = s"Bootstrap($endpoints)"
}

object Bootstrap extends Bootstrap[HNil, HNil](
  endpoints = HNil,
  includeDateHeader = true,
  includeServerHeader = true,
  negotiateContentType = false,
  enableMethodNotAllowed = false,
  enableUnsupportedMediaType = false
)
