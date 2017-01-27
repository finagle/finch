package io.finch.internal

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status, Version}
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

  private[this] def conformHttp(rep: Response, version: Version): Response = {
    rep.version = version
    rep.date = currentTime()
    rep
  }

  def apply(req: Request): Future[Response] = underlying(Input.request(req)) match {
    case EndpointResult.Matched(rem, out) if rem.isEmpty =>
      out.map(oa => conformHttp(oa.toResponse(tr, tre), req.version)).run
    case _ => Future.value(conformHttp(Response(Status.NotFound), req.version))
  }
}
