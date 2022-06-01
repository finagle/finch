package io.finch

import cats.effect.std.Dispatcher
import cats.effect.{Async, Resource}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Filter, Http, ListeningServer, Service}
import io.finch.internal.TwitterFutureConverter
import shapeless._

/** Bootstraps a Finagle HTTP listening server out of the collection of Finch endpoints.
  *
  * {{{
  * val api: Service[Request, Response] = Bootstrap[F]
  *  .configure(includeServerHeader = false, enableMethodNotAllowed = true)
  *  .serve[Application.Json](getUser :+: postUser)
  *  .serve[Text.Plain](healthcheck)
  *  .listen(":80")
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
    endpoints: ES,
    server: => Http.Server = Http.server,
    filter: Filter[Request, Response, Request, Response] = Filter.identity[Request, Response],
    middleware: Endpoint.Compiled[F] => Endpoint.Compiled[F] = identity[Endpoint.Compiled[F]] _,
    includeDateHeader: Boolean = true,
    includeServerHeader: Boolean = true,
    enableMethodNotAllowed: Boolean = false,
    enableUnsupportedMediaType: Boolean = false
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

  private[finch] def compile(implicit ts: Compile[F, ES, CTS]): Endpoint.Compiled[F] = {
    val opts = Compile.Options(
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

    val ctx = Compile.Context()

    middleware(ts.apply(endpoints, opts, ctx))
  }

  private def service(compiled: Endpoint.Compiled[F])(implicit F: Async[F]): Resource[F, Service[Request, Response]] =
    Dispatcher[F].flatMap { dispatcher =>
      Resource.make(F.pure(ToService(compiled, dispatcher))) { service =>
        F.defer(service.close().toAsync[F])
      }
    }

  def toService(implicit F: Async[F], ts: Compile[F, ES, CTS]): Resource[F, Service[Request, Response]] =
    service(compile)

  private def listen(service: Service[Request, Response], address: String)(implicit F: Async[F]): Resource[F, ListeningServer] = {
    val filtered = filter.andThen(service)
    Resource.make(F.delay(server.serve(address, filtered))) { listening =>
      F.defer(listening.close().toAsync[F])
    }
  }

  def listen(address: String)(implicit F: Async[F], ts: Compile[F, ES, CTS]): Resource[F, ListeningServer] =
    for {
      service <- toService
      server <- listen(service, address)
    } yield server

  final override def toString: String = s"Bootstrap($endpoints)"
}

object Bootstrap {
  def apply[F[_]]: Bootstrap[F, HNil, HNil] = apply[F](Http.server)
  def apply[F[_]](server: Http.Server): Bootstrap[F, HNil, HNil] = new Bootstrap(HNil, server)
}
