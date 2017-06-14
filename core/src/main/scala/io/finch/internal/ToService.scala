package io.finch.internal

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status, Version}
import com.twitter.util.Future
import io.finch.{Endpoint, EndpointResult, Input, Output}
import scala.annotation.implicitNotFound
import shapeless._

/**
 * Wraps a given list of [[Endpoint]]s and their content-types with a Finagle [[Service]].
 *
 * Guarantees to:
 *
 * - handle Finch's own errors (i.e., [[Error]] and [[Error]]) as 400s
 * - supply the date header to each response
 * - copy requests's HTTP version onto a response
 * - respond with 404 when en endpoint is not matched
 */
@implicitNotFound(
"""An Endpoint you're trying to convert into a Finagle service is missing one or more encoders.

  Make sure each endpoint in ${ES} is one of the following:

  * A com.twitter.finagle.http.Response
  * A value of a type with an io.finch.Encode instance (with the corresponding content-type)
  * A coproduct made up of some combination of the above

  See https://github.com/finagle/finch/blob/master/docs/cookbook.md#fixing-the-toservice-compile-error
"""
)
trait ToService[ES <: HList, CTS <: HList] {
  def apply(endpoints: ES): Service[Request, Response]
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

  private def conformHttp(rep: Response, version: Version): Response = {
    rep.version = version
    rep.date = currentTime()
    rep
  }

  implicit val hnilTS: ToService[HNil, HNil] = new ToService[HNil, HNil] {
    def apply(endpoints: HNil): Service[Request, Response] = new Service[Request, Response] {
      def apply(req: Request): Future[Response] =
        Future.value(conformHttp(Response(Status.NotFound), req.version))
    }
  }

  implicit def hlistTS[A, EH <: Endpoint[A], ET <: HList, CTH <: String, CTT <: HList](implicit
    trA: ToResponse.Aux[A, CTH],
    trE: ToResponse.Aux[Exception, CTH],
    tsT: ToService[ET, CTT]
  ): ToService[Endpoint[A] :: ET, CTH :: CTT] = new ToService[Endpoint[A] :: ET, CTH :: CTT] {
    def apply(endpoints: Endpoint[A] :: ET): Service[Request, Response] =
      new Service[Request, Response] {
        private[this] val underlying = endpoints.head.handle(respond400OnErrors)

        def apply(req: Request): Future[Response] = underlying(Input.fromRequest(req)) match {
          case EndpointResult.Matched(rem, out) if rem.route.isEmpty =>
            out.map(oa => conformHttp(oa.toResponse(trA, trE), req.version)).run
          case _ => tsT(endpoints.tail)(req)
        }
      }
  }
}
