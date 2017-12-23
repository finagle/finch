package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status, Version}
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
 * - respond with 404 when en endpoint is not matched

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
  def apply(
    endpoints: ES,
    includeDateHeader: Boolean,
    includeServerHeader: Boolean,
    negotiateContentType: Boolean
  ): Service[Request, Response]
}

/**
 * Wraps a given [[Endpoint]] with a Finagle [[Service]].
 *
 * Guarantees to:
 *
 * - handle Finch's own errors (i.e., [[Error]] and [[Error]]) as 400s
 * - copy requests's HTTP version onto a response
 * - respond with 404 when en endpoint is not matched
 */
object ToService {

  private val respond400OnErrors: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error => Output.failure(e, Status.BadRequest)
    case es: io.finch.Errors => Output.failure(es, Status.BadRequest)
  }

  private def conformHttp(
      rep: Response,
      version: Version,
      includeDateHeader: Boolean,
      includeServerHeader: Boolean): Response = {

    rep.version = version

    if (includeDateHeader) {
      rep.date = currentTime()
    }

    if (includeServerHeader) {
      rep.server = "Finch"
    }

    rep
  }

  implicit val hnilTS: ToService[HNil, HNil] = new ToService[HNil, HNil] {
    def apply(
        endpoints: HNil,
        includeDateHeader: Boolean,
        includeServerHeader: Boolean,
        negotiateContentType: Boolean): Service[Request, Response] = new Service[Request, Response] {

      def apply(req: Request): Future[Response] = Future.value(
        conformHttp(Response(Status.NotFound), req.version, includeDateHeader, includeServerHeader)
      )
    }
  }

  implicit def hlistTS[A, EH <: Endpoint[A], ET <: HList, CTH, CTT <: HList](implicit
    ntrA: NegotiateToResponse[A, CTH],
    ntrE: NegotiateToResponse[Exception, CTH],
    tsT: ToService[ET, CTT]
  ): ToService[Endpoint[A] :: ET, CTH :: CTT] = new ToService[Endpoint[A] :: ET, CTH :: CTT] {
    def apply(
        endpoints: Endpoint[A] :: ET,
        includeDateHeader: Boolean,
        includeServerHeader: Boolean,
        negotiateContentType: Boolean): Service[Request, Response] = new Service[Request, Response] {

      private[this] val underlying = endpoints.head.handle(respond400OnErrors)

      def apply(req: Request): Future[Response] = underlying(Input.fromRequest(req)) match {
        case EndpointResult.Matched(rem, out) if rem.route.isEmpty =>
          val accept = if (negotiateContentType) req.accept.flatMap(a => Accept(a)) else Seq.empty
          out.map(oa => conformHttp(
            oa.toResponse(ntrA(accept), ntrE(accept)),
            req.version,
            includeDateHeader,
            includeServerHeader
          )).run

        case _ => tsT(endpoints.tail, includeDateHeader, includeServerHeader, negotiateContentType)(req)
      }
    }
  }

}
