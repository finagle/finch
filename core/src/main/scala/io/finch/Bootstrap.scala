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
) {

  class Serve[CT] {
    def apply[E](e: Endpoint[F, E]): Bootstrap[F, Endpoint[F, E] :: ES, CT :: CTS] =
      new Bootstrap(
        e :: endpoints,
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
      includeDateHeader: Boolean = includeDateHeader,
      includeServerHeader: Boolean = includeServerHeader,
      enableMethodNotAllowed: Boolean = enableMethodNotAllowed,
      enableUnsupportedMediaType: Boolean = enableUnsupportedMediaType
  ): Bootstrap[F, ES, CTS] = new Bootstrap(
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
    new Bootstrap(
      endpoints,
      server,
      f.andThen(filter),
      middleware,
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

  def middleware(f: Endpoint.Compiled[F] => Endpoint.Compiled[F]): Bootstrap[F, ES, CTS] =
    new Bootstrap(
      endpoints,
      server,
      filter,
      f.andThen(middleware),
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

  private[finch] def compile(implicit ts: Compile[F, ES, CTS]): Endpoint.Compiled[F] = {
    val options = Compile.Options(
      includeDateHeader,
      includeServerHeader,
      enableMethodNotAllowed,
      enableUnsupportedMediaType
    )

    middleware(ts(endpoints, options, Compile.Context()))
  }

  def toService(implicit F: Async[F], ts: Compile[F, ES, CTS]): Resource[F, Service[Request, Response]] = {
    val compiled = compile
    Dispatcher[F].flatMap { dispatcher =>
      Resource.make(F.pure(ToService(compiled, dispatcher))) { service =>
        F.defer(service.close().toAsync)
      }
    }
  }

  def listen(address: String)(implicit F: Async[F], ts: Compile[F, ES, CTS]): Resource[F, ListeningServer] =
    toService.flatMap { service =>
      val filtered = filter.andThen(service)
      Resource.make(F.delay(server.serve(address, filtered))) { listening =>
        F.defer(listening.close().toAsync)
      }
    }

  final override def toString: String =
    s"Bootstrap($endpoints)"
}

object Bootstrap {
  def apply[F[_]]: Bootstrap[F, HNil, HNil] = Bootstrap(Http.server)
  def apply[F[_]](server: Http.Server): Bootstrap[F, HNil, HNil] = new Bootstrap(HNil, server)
}
