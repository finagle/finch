package io.finch

import com.twitter.util.Future
import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Response, Request, Method}
import com.twitter.finagle.httpx.path.Path
import com.twitter.finagle.httpx.service.NotFoundService

/**
 * A REST API endpoint that primary defines a ''route'' and might be implicitly
 * converted into Finagle service ''Service[Req, Rep]'' if there is an implicit
 * view from ''Req'' to ''Request'' available in the scope.
 *
 * @tparam Req the request type
 * @tparam Rep the response type
 */
@deprecated(message = "Endpoint is deprecated in favor of coproduct routers", since = "0.8.0")
trait Endpoint[Req, Rep] { self =>

  /**
   * A rich route of this endpoint.
   *
   * @return a route of this endpoint
   */
  def route: PartialFunction[(Method, Path), Service[Req, Rep]]

  /**
   * Combines this endpoint with ''that'' endpoint. A new endpoint
   * contains routes of both this and ''that'' endpoint.
   *
   * @param that the endpoint to be combined with
   *
   * @return a new endpoint
   */
  def orElse(that: Endpoint[Req, Rep]): Endpoint[Req, Rep] = orElse(that.route)

  /**
   * Combines this endpoint with ''that'' partial function that defines
   * a route. A new endpoint contains routes of both this endpoint and ''that''
   * partial function
   *
   * @param that the partial function to be combined with
   *
   * @return a new endpoint
   */
  def orElse(that: PartialFunction[(Method, Path), Service[Req, Rep]]): Endpoint[Req, Rep] =
    new Endpoint[Req, Rep] {
      override def route = self.route orElse that
    }

  /**
   * Applies given function ''fn'' to every route's endpoints of this endpoint.
   *
   * @param fn the function to be applied
   *
   * @return a new endpoint
   */
  def andThen[ReqOut, RepOut](fn: Service[Req, Rep] => Service[ReqOut, RepOut]): Endpoint[ReqOut, RepOut] =
    new Endpoint[ReqOut, RepOut] {
      override def route = self.route andThen fn
    }

  /**
   * Composes this endpoint with given ''next'' service.
   *
   * @param next the service to compose
   * @tparam RepOut the response type
   *
   * @return an endpoint composed with filter
   */
  def ![RepOut](next: Service[Rep, RepOut]): Endpoint[Req, RepOut] =
    andThen { service =>
      new Service[Req, RepOut] {
        def apply(req: Req): Future[RepOut] = service(req) flatMap next
      }
    }
}

/**
 * A companion object for ''Endpoint''
 */
@deprecated(message = "Endpoint is deprecated in favor of coproduct routers", since = "0.8.0")
object Endpoint {

  /**
   * Joins given sequence of endpoints by orElse-ing them.
   *
   * @param endpoints the sequence of endpoints to join
   * @tparam Req a request type
   * @tparam Rep a response type
   *
   * @return a joined endpoint
   */
  def join[Req, Rep](endpoints: Endpoint[Req, Rep]*) : Endpoint[Req, Rep] =
    endpoints.reduce(_ orElse _)

  /**
   * A robust 404 respond for missing endpoints.
   */
  val NotFound: Endpoint[Request, Response] = new Endpoint[Request, Response] {
    private val underlying = new NotFoundService[Request]
    override def route = { case _ => underlying }
  }

  /**
   * Creates a new ''Endpoint'' using the given ''r'' function.
   *
   * @param r The route for the new Endpoint
   */
  def apply[Req, Rep](r: PartialFunction[(Method, Path), Service[Req, Rep]]): Endpoint[Req, Rep] =
    new Endpoint[Req, Rep] { override def route = r }

  /**
   * Allows to use an ''Endpoint'' when ''Service'' is expected. The implicit
   * conversion is possible if there is an implicit view from ''Req'' to
   * ''Request'' available in the scope.
   *
   * {{{
   *   val e: Endpoint[Request, Response] = ???
   *   Httpx.serve(new InetSocketAddress(8081), e)
   * }}}
   *
   * @param e the endpoint to convert
   * @param ev the evidence of implicit view
   * @tparam Req the request type
   * @tparam Rep the response type
   *
   * @return a service that delegates the requests to the underlying endpoint
   */
  @deprecated(message = "Endpoint is deprecated in favor of coproduct routers", since = "0.8.0")
  implicit def endpointToService[Req, Rep](e: Endpoint[Req, Rep])(implicit ev: Req => Request): Service[Req, Rep] =
    new Service[Req, Rep] {
      def apply(req: Req): Future[Rep] = e.route(req.method -> Path(req.path))(req)
    }
}
