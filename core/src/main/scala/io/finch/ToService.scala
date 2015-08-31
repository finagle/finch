package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Future
import io.finch.response.{EncodeResponse, NotFound, Ok}
import shapeless.ops.coproduct.Folder
import shapeless.{Coproduct, Poly1}

import scala.annotation.implicitNotFound

/**
 * Represents a conversion from a router returning a result type `A` to a
 * Finagle service from a request-like type `R` to a [[com.twitter.finagle.httpx.Response]].
 */
@implicitNotFound(
"""You can only convert a router into a Finagle service if the result type of the router is one of the following:

  * An Response
  * A value of a type with an EncodeResponse instance
  * A coproduct made up of some combination of the above

${A} does not satisfy the requirement. You may need to provide an EncodeResponse instance for ${A} (or for some
part of ${A}).
"""
)
trait ToService[A] {
  def apply(router: Endpoint[A]): Service[Request, Response]
}

object ToService extends LowPriorityToServiceInstances {
  /**
   * An instance for coproducts with appropriately typed elements.
   */
  implicit def coproductRouterToService[C <: Coproduct](implicit
    folder: Folder.Aux[EncodeAll.type, C, Response]
  ): ToService[C] = new ToService[C] {
    def apply(router: Endpoint[C]): Service[Request, Response] =
      routerToService(router.map(folder(_)))
  }
}

trait LowPriorityToServiceInstances {
  /**
   * An instance for types that can be transformed into a Finagle service.
   */
  implicit def valueRouterToService[A](implicit
    polyCase: EncodeAll.Case.Aux[A, Response]
  ): ToService[A] = new ToService[A] {
    def apply(router: Endpoint[A]): Service[Request, Response] =
      routerToService(router.map(polyCase(_)))
  }

  protected def routerToService(router: Endpoint[Response]): Service[Request, Response] =
    new Service[Request, Response] {
       import Endpoint._
       def apply(req: Request): Future[Response] = router(Input(req)) match {
         case Some((input, result)) if input.isEmpty => result()
         case _ => NotFound().toFuture
       }
    }

  /**
   * A polymorphic function value that accepts types that can be transformed into a Finagle service from a request-like
   * type to a [[com.twitter.finagle.httpx.Response]].
   */
  protected object EncodeAll extends Poly1 {
    /**
     * Transforms an [[com.twitter.finagle.httpx.Response]] directly into a constant service.
     */
    implicit def response: Case.Aux[Response, Response] =
      at(r => r)

    /**
     * Transforms an encodeable value into a constant service.
     */
    implicit def encodeable[A: EncodeResponse]: Case.Aux[A, Response] =
      at(a => Ok(a))
  }
}
