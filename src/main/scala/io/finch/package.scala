package io

import com.twitter.util.Future
import com.twitter.finagle.{Service, Filter}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.Path
import com.twitter.finagle.builder.ServerBuilder
import scala.util.parsing.json.{JSONType, JSONArray, JSONObject}
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpMethod}
import scala.util.Random
import com.twitter.finagle.http.{Http, Status, Version, Response, Request, RichHttp}

/**
 *
 *
 */
package object finch {
  type HttpRequest = Request
  type HttpResponse = Response
  type JsonResponse = JSONType

  trait HttpServiceOf[+Rep] extends Service[HttpRequest, Rep] {
    implicit class AnyToFuture[A](any: A) {
      def toFuture: Future[A] = Future.value(any)
    }
  }

  trait HttpService extends HttpServiceOf[HttpResponse]
  trait Facet[+RepIn, -RepOut] extends Filter[HttpRequest, RepOut, HttpRequest, RepIn]

  object JsonObject {
    def apply(args: (String, Any)*) = JSONObject(args.toMap)
  }

  object JsonArray {
    def apply(args: JSONObject*) = JSONArray(args.toList)
  }

  object TurnJsonToHttp extends Facet[JsonResponse, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, JsonResponse]) =
      service(req) flatMap { json =>
        val rep = Response(Version.Http11, Status.Ok)
        rep.setContentTypeJson()
        rep.setContentString(json.toString())

        Future.value(rep)
      }
  }

  class TurnJsonToHttpWithStatusFrom(statusTag: String) extends Facet[JsonResponse, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, JsonResponse]) =
      service(req) flatMap { json =>
        val status = json match {
          case JSONObject(map) =>
            map.get(statusTag) match {
              case Some(code: Int) => HttpResponseStatus.valueOf(code)
              case _ => Status.Ok
            }
          case _ => Status.Ok
        }

        val rep = Response(Version.Http11, status)
        rep.setContentTypeJson()
        rep.setContentString(json.toString())

        Future.value(rep)
      }
  }

  /**
   *
   */
  trait Resource { self =>

    /**
     * Returns a route itself.
     *
     * @return
     */
    def route: PartialFunction[(HttpMethod, Path), Service[HttpRequest, HttpResponse]]

    /**
     *
     * @param that
     * @return
     */
    def orElse(that: Resource) = new Resource {
      def route = self.route orElse that.route
    }

    /**
     *
     * @param fn
     * @return
     */
    def andThen(fn: Service[HttpRequest, HttpResponse] => Service[HttpRequest, HttpResponse]) =
      new Resource {
        def route = self.route andThen fn
      }

    implicit class AfterThatService[+RepIn](service: Service[HttpRequest, RepIn]) {
      def afterThat[A](thatFacet: Facet[RepIn, A]) =
        thatFacet andThen service
    }

    implicit class AfterThatFacet[+RepIn, -RepOut](facet: Facet[RepIn, RepOut]) {
      def afterThat[A](thatFacet: Facet[RepOut, A]) =
        thatFacet andThen facet
    }
  }

  /**
   *
   */
  class RestApi extends App {

    implicit class FilterAndThenResource(filter: Filter[HttpRequest, HttpResponse, HttpRequest, HttpResponse]) {
      def andThen(resource: => Resource) = resource andThen { filter andThen _ }
    }

    def name = "FinchInstance-" + new Random().alphanumeric.take(20)

    /**
     *
     * @param port
     * @param resource
     * @return
     */
    def exposeAt(port: Int)(resource: => Resource): Unit = {

      val service = new RoutingService[HttpRequest](
        new PartialFunction[HttpRequest, Service[HttpRequest, HttpResponse]] {
          def apply(req: HttpRequest) = resource.route(req.method -> Path(req.path))
          def isDefinedAt(req: HttpRequest) = resource.route.isDefinedAt(req.method -> Path(req.path))
        })

      ServerBuilder()
        .codec(RichHttp[HttpRequest](Http()))
        .bindTo(new InetSocketAddress(port))
        .name(name)
        .build(service)
    }
  }
}
