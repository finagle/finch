package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Method, Request, Response, Status, Version}
import com.twitter.util.Future
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
trait ToService[ES <: HList, CTS <: HList] {
  def apply(endpoints: ES, options: ToService.Options, context: ToService.Context): Service[Request, Response]
}

object ToService {

  /**
   * HTTP options propagated from [[Bootstrap]].
   */
  final case class Options(
    includeDateHeader: Boolean,
    includeServerHeader: Boolean,
    negotiateContentType: Boolean,
    enableMethodNotAllowed: Boolean,
    enableTracing: Boolean
  )

  /**
   * HTTP context propagated between endpoints.
   *
   * - `wouldAllow`: when non-empty, indicates that the incoming method wasn't allowed/matched
   */
  final case class Context(wouldAllow: List[Method] = Nil)

  private val respond400OnErrors: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error => Output.failure(e, Status.BadRequest)
    case es: io.finch.Errors => Output.failure(es, Status.BadRequest)
  }

  private def conformHttp(rep: Response, version: Version, options: Options): Response = {
    rep.version = version

    if (options.includeDateHeader) {
      rep.date = currentTime()
    }

    if (options.includeServerHeader) {
      rep.server = "Finch"
    }

    rep
  }

  implicit val hnilTS: ToService[HNil, HNil] = new ToService[HNil, HNil] {
    def apply(es: HNil, opts: Options, ctx: Context): Service[Request, Response] =
      new Service[Request, Response] {
        def apply(req: Request): Future[Response] = {
          val rep = Response()

          if (ctx.wouldAllow.nonEmpty && opts.enableMethodNotAllowed) {
            rep.status = Status.MethodNotAllowed
            rep.allow = ctx.wouldAllow
          } else {
            rep.status = Status.NotFound
          }

          if (opts.enableTracing) {
            rep.ctx.update(FinchContext.EndpointTrace, Trace.empty)
          }

          Future.value(conformHttp(rep, req.version, opts))
        }
      }
  }

  implicit def hlistTS[A, EH <: Endpoint[A], ET <: HList, CTH, CTT <: HList](implicit
    ntrA: ToResponse.Negotiable[A, CTH],
    ntrE: ToResponse.Negotiable[Exception, CTH],
    tsT: ToService[ET, CTT]
  ): ToService[Endpoint[A] :: ET, CTH :: CTT] = new ToService[Endpoint[A] :: ET, CTH :: CTT] {
    def apply(es: Endpoint[A] :: ET, opts: Options, ctx: Context): Service[Request, Response] =
      new Service[Request, Response] {
        private[this] val underlying = es.head.handle(respond400OnErrors)

        def apply(req: Request): Future[Response] = underlying(Input.fromRequest(req)) match {
          case EndpointResult.Matched(rem, trace, out) if rem.route.isEmpty =>

            val accept =
              if (opts.negotiateContentType) req.accept.map(a => Accept.fromString(a)) else Nil
            out.map { oa =>
              val response = oa.toResponse(ntrA(accept), ntrE(accept))
              if (opts.enableTracing) {
                response.ctx.update(FinchContext.EndpointTrace, trace)
              }
              conformHttp(response, req.version, opts)
            }.run

          case EndpointResult.NotMatched.MethodNotAllowed(allowed) =>
            tsT(es.tail, opts, ctx.copy(wouldAllow = ctx.wouldAllow ++ allowed))(req)

          case _ =>
            tsT(es.tail, opts, ctx)(req)
        }
    }
  }

}
