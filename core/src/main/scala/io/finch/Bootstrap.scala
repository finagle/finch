package io.finch

import cats.effect.std.Dispatcher
import cats.effect.{Async, Resource}
import com.twitter.finagle.Filter
import com.twitter.finagle.Http
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.http.{Request, Response}
import io.finch.internal.TwitterFutureConverter
import shapeless._

/** Bootstraps a Finagle HTTP service out of the collection of Finch endpoints.
  *
  * {{{
  * val api: Service[Request, Response] = Bootstrap
  *  .configure(negotiateContentType = true, enableMethodNotAllowed = true)
  *  .serve[Application.Json](getUser :+: postUser)
  *  .serve[Text.Plain](healthcheck)
  *  .toService
  * }}}
  *
  * ==Supported Configuration Options==
  *
  *   - `includeDateHeader` (default: `true`): whether or not to include the Date header into each response (see RFC2616, section 14.18)
  *
  *   - `includeServerHeader` (default: `true`): whether or not to include the Server header into each response (see RFC2616, section 14.38)
  *
  *   - `enableMethodNotAllowed` (default: `false`): whether or not to enable 405 MethodNotAllowed HTTP response (see RFC2616, section 10.4.6)
  *
  *   - `enableUnsupportedMediaType` (default: `false`) whether or not to enable 415 UnsupportedMediaType HTTP response (see RFC7231, section 6.5.13)
  *
  * @see
  *   https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
  * @see
  *   https://www.w3.org/Protocols/rfc2616/rfc2616-sec12.html
  * @see
  *   https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
  * @see
  *   https://tools.ietf.org/html/rfc7231#section-6.5.13
  */
class Bootstrap[F[_], ES <: HList, CTS <: HList](
    val endpoints: ES,
    val server: Http.Server = Http.server,
    val filter: Filter[Request, Response, Request, Response] = Filter.identity[Request, Response],
    val middleware: Endpoint.Compiled[F] => Endpoint.Compiled[F] = identity _,
    val includeDateHeader: Boolean = true,
    val includeServerHeader: Boolean = true,
    val enableMethodNotAllowed: Boolean = false,
    val enableUnsupportedMediaType: Boolean = false
) { self =>

  class Serve[CT] {
    def apply[E](e: Endpoint[F, E]): Bootstrap[F, Endpoint[F, E] :: ES, CT :: CTS] =
      new Bootstrap[F, Endpoint[F, E] :: ES, CT :: CTS](
        e :: self.endpoints,
        server,
        filter,
        middleware,
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
    server,
    filter,
    middleware,
    includeDateHeader,
    includeServerHeader,
    enableMethodNotAllowed,
    enableUnsupportedMediaType
  )

  def serve[CT]: Serve[CT] = new Serve[CT]

  def filter(f: Filter[Request, Response, Request, Response]): Bootstrap[F, ES, CTS] =
    new Bootstrap[F, ES, CTS](
      endpoints,
      server,
      f.andThen(filter),
      middleware,
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

  def filter(f: Endpoint.Compiled[F] => Endpoint.Compiled[F]): Bootstrap[F, ES, CTS] =
    new Bootstrap[F, ES, CTS](
      endpoints,
      server,
      filter,
      middleware.compose(f),
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

  private def compile(implicit ts: Compile[F, ES, CTS]): Endpoint.Compiled[F] = {
    val opts = Compile.Options(
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

    val ctx = Compile.Context()

    ts.apply(endpoints, opts, ctx)
  }

  def listen(address: String)(implicit F: Async[F], ts: Compile[F, ES, CTS]): Resource[F, ListeningServer] = {
    val compiled = middleware(compile)

    val serviced = Dispatcher[F].flatMap { dispatcher =>
      Resource.make(F.pure(ToService(compiled, dispatcher))) { service =>
        F.defer(service.close().toAsync[F])
      }
    }

    serviced.flatMap { service =>
      val filtered = filter.andThen(service)
      Resource.make(F.pure(server.serve(address, filtered))) { listening =>
        F.defer(listening.close().toAsync[F])
      }
    }
  }

  final override def toString: String = s"Bootstrap($endpoints)"
}

object Bootstrap {
  def apply[F[_]]: Bootstrap[F, HNil, HNil] = apply[F](Http.server)
  def apply[F[_]](server: Http.Server): Bootstrap[F, HNil, HNil] = new Bootstrap(HNil, server)
}
