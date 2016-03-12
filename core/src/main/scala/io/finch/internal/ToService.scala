package io.finch.internal

import scala.annotation.implicitNotFound

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import io.finch.{Endpoint, Input, Output}

/**
 * Represents a conversion from an [[Endpoint]] returning a result type `A` to a Finagle service
 * from a request-like type `R` to a [[Response]].
 *
 * @groupname LowPriorityToService Low priority `ToService`
 * @groupprio LowPriorityToService 0
 */
@implicitNotFound(
"""You can only convert an endpoint into a Finagle service if the result type of the router is one of
the following:

  * A Response
  * A value of a type with an Encode instance
  * A coproduct made up of some combination of the above

${A} does not satisfy the requirement. You may need to provide an EncodeResponse instance for ${A}
(or for some  part of ${A}).
"""
)
trait ToService[A] {
  type ContentType <: String

  def apply(endpoint: Endpoint[A]): Service[Request, Response]
}

object ToService {

  type Aux[A, CT <: String] = ToService[A] { type ContentType = CT }

  /**
   * Constructs an instance for a given type.
   * @group LowPriorityToService
   */
  def instance[A, CT <: String](f: Endpoint[A] => Service[Request, Response]): Aux[A, CT] =
    new ToService[A] {
      type ContentType = CT
      def apply(endpoint: Endpoint[A]): Service[Request, Response] = f(endpoint)
    }

  private[finch] val basicEndpointHandler: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error => Output.failure(e, Status.BadRequest)
  }

  /**
   * An instance for types that can be transformed into a Finagle service.
   * @group LowPriorityToService
   */
  implicit def endpointToService[A, CT <: String](implicit
    tra: ToResponse.Aux[A, CT],
    tre: ToResponse.Aux[Exception, CT]
  ): ToService.Aux[A, CT] = instance(e =>
    new Service[Request, Response] {
      private[this] val safeEndpoint = e.handle(basicEndpointHandler)

      def apply(req: Request): Future[Response] = safeEndpoint(Input(req)) match {
        case Some((remainder, output)) if remainder.isEmpty =>
          output.map(f => f.map(o => o.toResponse(req.version))).value
        case _ => Future.value(Response(req.version, Status.NotFound))
      }
    }
  )
}
