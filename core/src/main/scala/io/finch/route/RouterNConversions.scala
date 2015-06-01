package io.finch.route

import com.twitter.finagle.Service
import com.twitter.util.Future
import io.finch._
import io.finch.request.{RequestReader, ToRequest}
import io.finch.response.{EncodeResponse, Ok, NotFound}
import shapeless.{Coproduct, Poly1}
import shapeless.ops.coproduct.Folder

/**
 * Implicit conversions to and from [[RouterN]].
 */
trait RouterNConversions {

  private val respondNotFound: Future[HttpResponse] = NotFound().toFuture
  private def routerToService[R: ToRequest](r: RouterN[Service[R, HttpResponse]]): Service[R, HttpResponse] =
    Service.mk[R, HttpResponse] { req =>
      r(requestToRoute[R](implicitly[ToRequest[R]].apply(req))) match {
        case Some((Nil, service)) => service(req)
        case _ => respondNotFound
      }
    }

  /**
   * An implicit conversion that turns any value router where all elements can be converted into responses into a
   * service that returns responses.
   */
  implicit def valueRouterToService[R: ToRequest, A](
    router: RouterN[A]
  )(implicit
    polyCase: EncodeAll.Case.Aux[A, Service[R, HttpResponse]]
  ): Service[R, HttpResponse] = routerToService(router.map(polyCase))

  /**
   * An implicit conversion that turns any coproduct endpoint where all elements can be converted into responses into
   * a service that returns responses.
   */
  implicit def coproductRouterToService[R: ToRequest, C <: Coproduct](
    router: RouterN[C]
  )(implicit
    folder: Folder.Aux[EncodeAll.type, C, Service[R, HttpResponse]]
  ): Service[R, HttpResponse] = routerToService(router.map(c => folder(c)))

  /**
   * An implicit conversion that turns any endpoint with an output type that can be converted into a response into a
   * service that returns responses.
   */
  implicit def endpointToHttpResponse[A, B](e: Endpoint[A, B])(implicit
    encoder: EncodeResponse[B]
  ): Endpoint[A, HttpResponse] = e.map { service =>
    new Service[A, HttpResponse] {
      def apply(a: A): Future[HttpResponse] = service(a).map(b => Ok(encoder(b)))
    }
  }

  /**
   * Implicitly converts the given `Router[Service[_, _]]` into a service.
   */
  implicit def endpointToService[Req, Rep](
    r: RouterN[Service[Req, Rep]]
  )(implicit ev: Req => HttpRequest): Service[Req, Rep] = new Service[Req, Rep] {
    def apply(req: Req): Future[Rep] = r(requestToRoute[Req](req)) match {
      case Some((Nil, service)) => service(req)
      case _ => RouteNotFound(s"${req.method.toString.toUpperCase} ${req.path}").toFutureException[Rep]
    }
  }

  /**
   * A polymorphic function value that accepts types that can be transformed into a Finagle service from a request-like
   * type to a [[HttpResponse]].
   */
  object EncodeAll extends Poly1 {
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
