package io.finch.internal

import scala.annotation.implicitNotFound

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import io.finch._
import shapeless._

@implicitNotFound("""
You can only convert an endpoint into a Finagle service if its result type is one of the following:

 * 'com.twitter.finagle.http.Response'
 * a value of type 'A' with 'io.finch.Encode' instance available (for requested Content-Type)
 * a 'shapeless.Coproduct' made up of some combination of the above

Make sure this rule evaluates for every endpoint passed to 'respond' method on 'ServiceBuilder'.
""")
trait ToService[ES <: HList, CTS <: HList] {
  def apply(endpoints: ES): Service[Request, Response]
}

object ToService {

  implicit val hnilToService: ToService[HNil, HNil] = new ToService[HNil, HNil] {
    def apply(endpoints: HNil): Service[Request, Response] =
      Service.mk(_ => Future.value(Response(Status.NotFound)))
  }

  implicit def hconsToService[A, EH <: Endpoint[A], ET <: HList, CTH <: String, CTT <: HList](implicit
    trH: ToResponse.Aux[Output[A], CTH],
    tsT: ToService[ET, CTT]
  ): ToService[Endpoint[A] :: ET, CTH :: CTT] = new ToService[Endpoint[A] :: ET, CTH :: CTT] {
     def apply(endpoints: Endpoint[A] :: ET): Service[Request, Response] = new Service[Request, Response] {
       private[this] val basicEndpointHandler: PartialFunction[Throwable, Output[Nothing]] = {
         case e: io.finch.Error => Output.failure(e, Status.BadRequest)
       }

       private[this] val safeEndpoint = endpoints.head.handle(basicEndpointHandler)

       def apply(req: Request): Future[Response] = safeEndpoint(Input(req)) match {
         case Some((remainder, output)) if remainder.isEmpty =>
           output.map(o => o.toResponse[CTH](req.version)).run
         case _ => tsT(endpoints.tail)(req)
       }
     }
  }
}
