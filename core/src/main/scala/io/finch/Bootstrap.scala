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
 * - `enableTracing` (default: `false`): whether or not to enable tracing of called endpoint propagated
 *   through Response.ctx
 *
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec12.html
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
 */
class Bootstrap[ES <: HList, CTS <: HList](
    val endpoints: ES,
    val includeDateHeader: Boolean = true,
    val includeServerHeader: Boolean = true,
    val negotiateContentType: Boolean = false,
    val enableMethodNotAllowed: Boolean = false,
    val enableTracing: Boolean = false) { self =>

  class Serve[CT] {
    def apply[E](e: Endpoint[E]): Bootstrap[Endpoint[E] :: ES, CT :: CTS] =
      new Bootstrap[Endpoint[E] :: ES, CT :: CTS](
        e :: self.endpoints,
        includeDateHeader,
        includeServerHeader,
        negotiateContentType,
        enableMethodNotAllowed,
        enableTracing
      )
    }

  def configure(
    includeDateHeader: Boolean = self.includeDateHeader,
    includeServerHeader: Boolean = self.includeServerHeader,
    negotiateContentType: Boolean = self.negotiateContentType,
    enableMethodNotAllowed: Boolean = self.enableMethodNotAllowed,
    enableTracing: Boolean = self.enableTracing
  ): Bootstrap[ES, CTS] = new Bootstrap[ES, CTS](
    endpoints,
    includeDateHeader,
    includeServerHeader,
    negotiateContentType,
    enableMethodNotAllowed,
    enableTracing
  )

  def serve[CT]: Serve[CT] = new Serve[CT]

  def toService(implicit ts: ToService[ES, CTS]): Service[Request, Response] = ts(
    endpoints,
    ToService.Options(
      includeDateHeader,
      includeServerHeader,
      negotiateContentType,
      enableMethodNotAllowed,
      enableTracing
    ),
    ToService.Context()
  )

  final override def toString: String = s"Bootstrap($endpoints)"
}

object Bootstrap extends Bootstrap[HNil, HNil](
    endpoints = HNil,
    includeDateHeader = true,
    includeServerHeader = true,
    negotiateContentType = false,
    enableMethodNotAllowed = false,
    enableTracing = false
)
