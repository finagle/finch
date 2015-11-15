package io.finch

import scala.annotation.implicitNotFound

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import shapeless.{Coproduct, Poly1}
import shapeless.ops.coproduct.Folder

/**
 * Represents a conversion from an [[Endpoint]] returning a result type `A` to a Finagle service from a request-like
 * type `R` to a [[Response]].
 */
@implicitNotFound(
"""You can only convert a router into a Finagle service if the result type of the router is one of the following:

  * A Response
  * A value of a type with an EncodeResponse instance
  * A coproduct made up of some combination of the above

${A} does not satisfy the requirement. You may need to provide an EncodeResponse instance for ${A} (or for some  part of
${A}).
"""
)
trait ToService[A] {
  def apply(endpoint: Endpoint[A]): Service[Request, Response]
}

object ToService extends LowPriorityToServiceInstances {
  /**
   * An instance for coproducts with appropriately typed elements.
   */
  implicit def coproductRouterToService[C <: Coproduct](implicit
    folder: Folder.Aux[EncodeAll.type, C, Response],
    exceptionToResponse: ToResponse[Exception]
  ): ToService[C] = new ToService[C] {
    def apply(e: Endpoint[C]): Service[Request, Response] = endpointToService(e.map(folder(_)))
  }
}

trait LowPriorityToServiceInstances {

  private[finch] val basicEndpointHandler: PartialFunction[Throwable, Output.Failure] = {
    case e: Error => Output.Failure(e, Status.BadRequest)
  }

  /**
   * An instance for types that can be transformed into a Finagle service.
   */
  implicit def valueRouterToService[A](implicit
    polyCase: EncodeAll.Case.Aux[A, Response],
    exceptionToResponse: ToResponse[Exception]
  ): ToService[A] = new ToService[A] {
    def apply(e: Endpoint[A]): Service[Request, Response] = endpointToService(e.map(polyCase(_)))
  }

  protected def endpointToService(e: Endpoint[Response])(implicit
    exceptionToResponse: ToResponse[Exception]
  ): Service[Request, Response] =
    new Service[Request, Response] {
      private[this] val safeEndpoint = e.handle(basicEndpointHandler)

      def apply(req: Request): Future[Response] = safeEndpoint(Input(req)) match {
        case Some((remainder, output)) if remainder.isEmpty =>
          output().map(o => o.toResponse(req.version)).handle {
            // TODO: Remove after Finagle 6.31
            case _ => Response(req.version, Status.InternalServerError)
          }
        case _ => Future.value(Response(req.version, Status.NotFound))
      }
  }

  /**
   * A polymorphic function value that accepts types that can be transformed into a Finagle service from a request-like
   * type to a [[Response]].
   */
  protected object EncodeAll extends Poly1 {
    /**
     * Transforms an encodeable value into a constant service.
     */
    implicit def toResponseToResponse[A](implicit toResponse: ToResponse[A]): Case.Aux[A, Response] =
      at(a => toResponse(a))
  }
}
