package io.finch.internal

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import io.finch.{Endpoint, EndpointResult, Input, Output}

/**
 * Wraps a given [[Endpoint]] with a Finagle [[Service]].
 *
 * Guarantees to:
 *
 * - handle Finch's own errors (i.e., [[Error]] and [[Error]]) as 400s
 * - copy requests's HTTP version onto a response
 * - respond with 404 when en endpoint is not matched
 */
private[finch] final class ToService[A, CT <: String](
    e: Endpoint[A],
    tr: ToResponse.Aux[A, CT],
    tre: ToResponse.Aux[Exception, CT]) extends Service[Request, Response] {

  private[this] val underlying = e.handle {
    case e: io.finch.Error => Output.failure(e, Status.BadRequest)
    case es: io.finch.Errors => Output.failure(es, Status.BadRequest)
  }

  private[this] def copyVersion(from: Request, to: Response): Response = {
    to.version = from.version
    to
  }

  def apply(req: Request): Future[Response] = underlying(Input.request(req)) match {
    case EndpointResult.Matched(rem, out) if rem.isEmpty =>
      out.map(oa => copyVersion(req, oa.toResponse(tr, tre))).run
    case _ => Future.value(Response(req.version, Status.NotFound))
  }
}
