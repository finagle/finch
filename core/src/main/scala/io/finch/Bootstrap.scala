package io.finch

import cats.effect.Async
import cats.effect.std.Dispatcher
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
  * == Supported Configuration Options ==
  *
  * - `includeDateHeader` (default: `true`): whether or not to include the Date header into
  *   each response (see RFC2616, section 14.18)
  *
  * - `includeServerHeader` (default: `true`): whether or not to include the Server header into
  *   each response (see RFC2616, section 14.38)
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
class Bootstrap[F[_], ES <: HList, CTS <: HList](
    val endpoints: ES,
    val includeDateHeader: Boolean = true,
    val includeServerHeader: Boolean = true,
    val enableMethodNotAllowed: Boolean = false,
    val enableUnsupportedMediaType: Boolean = false
) { self =>

  class Serve[CT] {
    def apply[FF[_], E](e: Endpoint[FF, E]): Bootstrap[FF, Endpoint[FF, E] :: ES, CT :: CTS] =
      new Bootstrap[FF, Endpoint[FF, E] :: ES, CT :: CTS](
        e :: self.endpoints,
        includeDateHeader,
        includeServerHeader,
        enableMethodNotAllowed,
        enableUnsupportedMediaType
      )
  }

  def configure(
      includeDateHeader: Boolean = self.includeDateHeader,
      includeServerHeader: Boolean = self.includeServerHeader,
      enableMethodNotAllowed: Boolean = self.enableMethodNotAllowed,
      enableUnsupportedMediaType: Boolean = self.enableUnsupportedMediaType
  ): Bootstrap[F, ES, CTS] = new Bootstrap[F, ES, CTS](
    endpoints,
    includeDateHeader,
    includeServerHeader,
    enableMethodNotAllowed,
    enableUnsupportedMediaType
  )

  def serve[CT]: Serve[CT] = new Serve[CT]

  def compile(implicit ts: Compile[F, ES, CTS]): Endpoint.Compiled[F] = {
    val opts = Compile.Options(
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

    val ctx = Compile.Context()

    ts.apply(endpoints, opts, ctx)
  }

  def toService(implicit F: Async[F], dispatcher: Dispatcher[F], ts: Compile[F, ES, CTS]): Service[Request, Response] =
    Endpoint.toService(compile)

  final override def toString: String = s"Bootstrap($endpoints)"
}

object Bootstrap
    extends Bootstrap[Id, HNil, HNil](
      endpoints = HNil,
      includeDateHeader = true,
      includeServerHeader = true,
      enableMethodNotAllowed = false,
      enableUnsupportedMediaType = false
    )
