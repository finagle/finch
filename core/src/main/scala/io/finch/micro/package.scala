package io.finch

import com.twitter.finagle.Service
import com.twitter.util.Future

import io.finch.route.{Endpoint => _, _}
import io.finch.response._
import io.finch.request._

/**
 * An experimental package that enables `micro`-services support in Finch.
 */
package object micro {

  /**
   * An alias for polymorphic [[PRequestReader]].
   */
  type PMicro[R, A] = PRequestReader[R, A]

  /**
   * A [[PMicro]] with request type fixed to [[HttpRequest]].
   */
  type Micro[A] = PMicro[HttpRequest, A]

  /**
   * A companion object for `Micro`.
   */
  val Micro = RequestReader

  /**
   * A [[Router]] that fetches a [[PMicro]] is called an endpoint.
   */
  type PEndpoint[R] = Router[PMicro[R, HttpResponse]]

  /**
   * A [[PEndpoint]] with request type fixed to [[HttpRequest]].
   */
  type Endpoint = PEndpoint[HttpRequest]

  implicit class MicroRouterOps[R, A](r: Router[PMicro[R, A]]) {
    def |[B](that: Router[PMicro[R, B]])(implicit eA: EncodeResponse[A], eB: EncodeResponse[B]): PEndpoint[R] =
      r.map(_.map(Ok(_))) orElse that.map(_.map(Ok(_)))
  }

  implicit def microToHttpMicro[R, A](m: PMicro[R, A])(
    implicit e: EncodeResponse[A]
  ): PMicro[R, HttpResponse] = m.map(Ok(_))

  implicit def microRouterToEndpoint[R, M](r: Router[M])(
    implicit ev: M => PMicro[R, HttpResponse]
  ): PEndpoint[R] = r.map(ev)

  implicit def endpointToFinagleService[M, R](r: Router[M])(
    implicit evM: M => PMicro[R, HttpResponse], evR: R %> HttpRequest
  ): Service[R, HttpResponse] = new Service[R, HttpResponse] {
    def apply(req: R): Future[HttpResponse] = {
      val httpReq = evR(req)
      r.map(evM)(requestToRoute(httpReq)) match {
        case Some((Nil, micro)) => micro(req)
        case _ => RouteNotFound(s"${httpReq.method.toString.toUpperCase} ${httpReq.path}").toFutureException
      }
    }
  }
}
