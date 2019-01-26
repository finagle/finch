package io.finch

import cats.{Applicative, MonadError}
import cats.syntax.all._
import com.twitter.finagle.http.{Method, Request, Response, Status, Version}
import io.finch.internal.currentTime
import scala.annotation.implicitNotFound
import shapeless._

/**
 * Wraps a given list of [[Endpoint]]s and their content-types with a Finagle [[Service]].
 *
 * Guarantees to:
 *
 * - handle Finch's own errors (i.e., [[Error]] and [[Error]]) as 400s
 * - copy requests's HTTP version onto a response
 * - respond with 404 when an endpoint is not matched
 * - respond with 405 when an endpoint is not matched because method wasn't allowed (serve back an `Allow` header)
 * - include the date header on each response (unless disabled)
 * - include the server header on each response (unless disabled)
 */
@implicitNotFound(
"""An Endpoint you're trying to convert into a Finagle service is missing one or more encoders.

  Make sure each endpoint in ${ES}, ${CTS} is one of the following:

  * A com.twitter.finagle.http.Response
  * A value of a type with an io.finch.Encode instance (with the corresponding content-type)
  * A coproduct made up of some combination of the above

  See https://github.com/finagle/finch/blob/master/docs/src/main/tut/cookbook.md#fixing-the-toservice-compile-error
"""
)
trait ToService[F[_], ES <: HList, CTS <: HList] {
  def apply(endpoints: ES, options: ToService.Options, context: ToService.Context): Service[F]
}

object ToService {

  /**
   * HTTP options propagated from [[Bootstrap]].
   */
  final case class Options(
    includeDateHeader: Boolean,
    includeServerHeader: Boolean,
    enableMethodNotAllowed: Boolean,
    enableUnsupportedMediaType: Boolean
  )

  /**
   * HTTP context propagated between endpoints.
   *
   * - `wouldAllow`: when non-empty, indicates that the incoming method wasn't allowed/matched
   */
  final case class Context(wouldAllow: List[Method] = Nil)

  private val respond400: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error => Output.failure(e, Status.BadRequest)
    case es: io.finch.Errors => Output.failure(es, Status.BadRequest)
  }

  private val respond415: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error if e.getCause eq Decode.UnsupportedMediaTypeException =>
      Output.failure(e, Status.UnsupportedMediaType)
  }

  private def conformHttp(rep: Response, version: Version, options: Options): Response = {
    rep.version = version

    if (options.includeDateHeader) {
      rep.headerMap.setUnsafe("Date", currentTime())
    }

    if (options.includeServerHeader) {
      rep.headerMap.setUnsafe("Server", "Finch")
    }

    rep
  }

  implicit def hnilTS[F[_]](implicit F: Applicative[F]): ToService[F, HNil, HNil] = new ToService[F, HNil, HNil] {
    def apply(es: HNil, opts: Options, ctx: Context): Service[F] =
      new Service[F] {
        def apply(req: Request): F[(Trace, Response)] = {
          val rep = Response()

          if (ctx.wouldAllow.nonEmpty && opts.enableMethodNotAllowed) {
            rep.status = Status.MethodNotAllowed
            rep.allow = ctx.wouldAllow
          } else {
            rep.status = Status.NotFound
          }

          F.pure(Trace.empty -> conformHttp(rep, req.version, opts))
        }
      }
  }

  type IsNegotiable[C] = OrElse[C <:< Coproduct, DummyImplicit]

  implicit def hlistTS[F[_], A, EH <: Endpoint[F, A], ET <: HList, CTH, CTT <: HList](implicit
    ntrA: ToResponse.Negotiable[F, A, CTH],
    ntrE: ToResponse.Negotiable[F, Exception, CTH],
    F: MonadError[F, Throwable],
    tsT: ToService[F, ET, CTT],
    isNegotiable: IsNegotiable[CTH]
  ): ToService[F, Endpoint[F, A] :: ET, CTH :: CTT] = new ToService[F, Endpoint[F, A] :: ET, CTH :: CTT] {
    def apply(es: Endpoint[F, A] :: ET, opts: Options, ctx: Context): Service[F] =
      new Service[F] {
        private[this] val handler =
          if (opts.enableUnsupportedMediaType) respond415.orElse(respond400) else respond400

        private[this] val negotiateContent = isNegotiable.fold(_ => true, _ => false)
        private[this] val underlying = es.head.handle(handler)

        def apply(req: Request): F[(Trace, Response)] = underlying(Input.fromRequest(req)) match {
          case EndpointResult.Matched(rem, trc, out) if rem.route.isEmpty =>

            val accept = if (negotiateContent) req.accept.map(a => Accept.fromString(a)).toList else Nil
            F.flatMap(out)(oa => oa.toResponse(F, ntrA(accept), ntrE(accept)).map(r => trc -> r))

          case EndpointResult.NotMatched.MethodNotAllowed(allowed) =>
            tsT(es.tail, opts, ctx.copy(wouldAllow = ctx.wouldAllow ++ allowed))(req)

          case _ =>
            tsT(es.tail, opts, ctx)(req)
        }
    }
  }

}
