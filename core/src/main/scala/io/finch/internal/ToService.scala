package io.finch.internal

import scala.annotation.implicitNotFound

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import io.finch.{Endpoint, Input, Output}
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

trait LowPriorityToServiceInstances {

  /**
   * Returns an instance for a given type.
   */
  def apply[A](implicit ts: ToService[A]): ToService[A] = ts

  /**
   * Constructs an instance for a given type.
   */
  def instance[A](f: Endpoint[A] => Service[Request, Response]): ToService[A] = new ToService[A] {
    def apply(endpoint: Endpoint[A]): Service[Request, Response] = f(endpoint)
  }

  private[finch] val basicEndpointHandler: PartialFunction[Throwable, Output.Failure] = {
    case e: io.finch.Error => Output.Failure(e, Status.BadRequest)
  }

  /**
   * An instance for types that can be transformed into a Finagle service.
   */
  implicit def valueRouterToService[A](implicit
    polyCase: EncodeAll.Case.Aux[A, Response],
    tre: ToResponse[Exception]
  ): ToService[A] = instance(e => endpointToService(e.map(polyCase(_))))

  protected def endpointToService(e: Endpoint[Response])(implicit
    tre: ToResponse[Exception]
  ): Service[Request, Response] =
    new Service[Request, Response] {
      private[this] val safeEndpoint = e.handle(basicEndpointHandler)

      def apply(req: Request): Future[Response] = safeEndpoint(Input(req)) match {
        case Some((remainder, output)) if remainder.isEmpty =>
          output.map(f => f.map(o => o.toResponse(req.version))).value.handle {
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
    implicit def toResponseToResponse[A](implicit tr: ToResponse[A]): Case.Aux[A, Response] =
      at(a => tr(a))
  }
}

object ToService extends LowPriorityToServiceInstances {
  /**
   * An instance for coproducts with appropriately typed elements.
   */
  implicit def coproductRouterToService[C <: Coproduct](implicit
    folder: Folder.Aux[EncodeAll.type, C, Response],
    tre: ToResponse[Exception]
  ): ToService[C] = instance(e => endpointToService(e.map(folder(_))))
}
