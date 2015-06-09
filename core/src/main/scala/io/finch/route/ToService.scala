package io.finch.route

import com.twitter.finagle.Service
import com.twitter.util.Future
import io.finch._
import io.finch.HttpResponse
import io.finch.request.{RequestReader, ToRequest}
import io.finch.response.{EncodeResponse, NotFound, Ok}
import scala.annotation.implicitNotFound
import shapeless.{Coproduct, Poly1}
import shapeless.ops.coproduct.Folder

/**
 * Represents a conversion from a router returning a result type `A` to a
 * Finagle service from a request-like type `R` to a [[HttpResponse]].
 */
@implicitNotFound(
"""You can only convert a router into a Finagle service from ${R} to an HttpResponse if ${R} can be
converted into an HttpRequest, and if the result type of the router is one of the following:

  * An HttpResponse
  * A value of a type with an EncodeResponse instance
  * A future of an HttpResponse
  * A future of a value of a type with an EncodeResponse instance
  * A RequestReader that returns a value of a type with an EncodeResponse instance
  * A Finagle service that returns an HttpResponse
  * A Finagle service that returns a value of a type with an EncodeResponse instance
  * A coproduct made up of some combination of the above

${A} does not satisfy the requirement. You may need to provide an EncodeResponse instance for
${A} (or for some part of ${A}).
"""
)
trait ToService[R, A] {
  def apply(router: Router[A]): Service[R, HttpResponse]
}

object ToService extends LowPriorityToServiceInstances {
  /**
   * An instance for coproducts with appropriately typed elements.
   */
  implicit def coproductRouterToService[R: ToRequest, C <: Coproduct](implicit
    folder: Folder.Aux[EncodeAll.type, C, Service[R, HttpResponse]]
  ): ToService[R, C] = new ToService[R, C] {
    def apply(router: Router[C]): Service[R, HttpResponse] = routerToService(router.map(folder(_)))
  }
}

trait LowPriorityToServiceInstances {
  /**
   * An instance for types that can be transformed into a Finagle service.
   */
  implicit def valueRouterToService[R: ToRequest, A](implicit
    polyCase: EncodeAll.Case.Aux[A, Service[R, HttpResponse]]
  ): ToService[R, A] = new ToService[R, A] {
    def apply(router: Router[A]): Service[R, HttpResponse] =
      routerToService(router.map(polyCase(_)))
  }

  protected def routerToService[R: ToRequest](r: Router[Service[R, HttpResponse]]): Service[R, HttpResponse] =
    Service.mk[R, HttpResponse] { req =>
      r(RouterInput(implicitly[ToRequest[R]].apply(req))) match {
        case Some((output, service)) if output.path.isEmpty => service(req)
        case _ => NotFound().toFuture
      }
    }

  /**
   * A polymorphic function value that accepts types that can be transformed into a Finagle service from a request-like
   * type to a [[HttpResponse]].
   */
  protected object EncodeAll extends Poly1 {
    /**
     * Transforms an [[HttpResponse]] directly into a constant service.
     */
    implicit def response[R: ToRequest]: Case.Aux[HttpResponse, Service[R, HttpResponse]] =
      at(r => Service.const(r.toFuture))

    /**
     * Transforms an encodeable value into a constant service.
     */
    implicit def encodeable[R: ToRequest, A: EncodeResponse]: Case.Aux[A, Service[R, HttpResponse]] =
      at(a => Service.const(Ok(a).toFuture))

    /**
     * Transforms an [[HttpResponse]] in a future into a constant service.
     */
    implicit def futureResponse[R: ToRequest]: Case.Aux[Future[HttpResponse], Service[R, HttpResponse]] =
      at(Service.const)

    /**
     * Transforms an encodeable value in a future into a constant service.
     */
    implicit def futureEncodeable[R: ToRequest, A: EncodeResponse]: Case.Aux[Future[A], Service[R, HttpResponse]] =
      at(fa => Service.const(fa.map(Ok(_))))

    /**
     * Transforms a [[RequestReader]] into a service.
     */
    implicit def requestReader[R: ToRequest, A: EncodeResponse]: Case.Aux[RequestReader[A], Service[R, HttpResponse]] =
      at(reader => Service.mk(req => reader(implicitly[ToRequest[R]].apply(req)).map(Ok(_))))

    /**
     * An identity transformation for services that return an [[HttpResponse]].
     *
     * Note that the service may have a static type that is more specific than `Service[R, HttpResponse]`.
     */
    implicit def serviceResponse[S, R](implicit
      ev: S => Service[R, HttpResponse],
      tr: ToRequest[R]
    ): Case.Aux[S, Service[R, HttpResponse]] =
      at(s => Service.mk(req => ev(s)(req)))

    /**
     * A transformation for services that return an encodeable value. Note that the service may have a static type that
     * is more specific than `Service[R, A]`.
     */
    implicit def serviceEncodeable[S, R, A](implicit
      ev: S => Service[R, A],
      tr: ToRequest[R],
      ae: EncodeResponse[A]
    ): Case.Aux[S, Service[R, HttpResponse]] =
      at(s => Service.mk(req => ev(s)(req).map(Ok(_))))
  }
}
