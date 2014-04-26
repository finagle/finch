/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of Finch library that may be found at
 *
 *      https://github.com/vkostyukov/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s): -
 */

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

/***
 * Hi! I'm Finch - a super-tiny library atop of Finagle that makes the
 * development of RESTFul API services more pleasant and slick.
 *
 * I'm built around three very simple building-blocks:
 *   1. ''HttpServiceOf[A]'' that maps ''HttpRequest'' to some response
 *      (both are just a special cases of Finagle's ''Service'')
 *   2. ''Facet[+A, -B]'' that transforms service's response ''A'' to ''B''
 *      (just a special case of Finagle's 'Filter')
 *   3. ''Resource'' that provides route information about a particular resource
 *      (just a special case of ''PartialFunction'' from route to ''HttpService'')
 *
 * I'm trying to follow the principles of my elder brother and keep the things
 * as composable as possible.
 *
 *   (a) In order to mark the difference between filters and facets and show the
 *       direction of a data-flow, the facets are composed with ''afterThat'' operator
 *       within reversed order:
 *
 *         '''val s = service afterThat facetA afterThat facetB'''

 *   (b) Resources might be treated as partial functions, so they may be composed
 *       together with ''orElse'' operator:
 *
 *         '''val r = userResource orElse orderResource'''

 *   (c) Another useful resource operator is ''andThen'' that takes a function from
 *       ''HttpService'' to ''HttpService'' and returns a new resource with function
 *       applied to its every service.
 *
 *   (d) Resources may also be composed with filters by using the ''andThen'' operator
 *       in a familiar way:
 *
 *         '''val r = authorize andThen resource'''
 *
 * I support the only single format - JSON. There are also two predefined facets
 * available for JSON data-types.
 *
 *   1. ''TurnJsonToHttp'' simply coverts the JSON data to HttpResponse
 *   2. ''TurnJsonToHttpWithStatus(statusTag)'' checks whether the received json
 *      response contains the specified ''statusTag'' and if so copies it to the
 *      ''HttpResponse''. Otherwise status ''200'' (HTTP OK) is used.
 *
 * Have fun writing a reusable and scalable code with me!
 *
 * - https://github.com/vkostyukov/finch
 * - http://vkostyukov.ru
 */
package object finch {
  type HttpRequest = Request
  type HttpResponse = Response
  type JsonResponse = JSONType

  /**
   * An HttpService with specified response type.
   *
   * @tparam Rep the response type
   */
  trait HttpServiceOf[+Rep] extends Service[HttpRequest, Rep] {
    implicit class AnyToFuture[A](any: A) {
      def toFuture: Future[A] = Future.value(any)
    }
  }

  /**
   * A pure HttpService.
   */
  trait HttpService extends HttpServiceOf[HttpResponse]

  /**
   * Facet implements Filter interface but has a different meaning. Facets are
   * converts services responses from ''RepIn'' to ''RepOut''.
   *
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait Facet[-RepIn, +RepOut] extends Filter[HttpRequest, RepOut, HttpRequest, RepIn]

  object JsonObject {
    def apply(args: (String, Any)*) = JSONObject(args.toMap)
  }

  object JsonArray {
    def apply(args: JSONObject*) = JSONArray(args.toList)
  }

  /**
   * A facet that turns a ''JsonResponse'' to an ''HttpResponse''.
   */
  object TurnJsonToHttp extends Facet[JsonResponse, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, JsonResponse]) =
      service(req) flatMap { json =>
        val rep = Response(Version.Http11, Status.Ok)
        rep.setContentTypeJson()
        rep.setContentString(json.toString())

        Future.value(rep)
      }
  }

  /**
   * A facet that turns a ''JsonResponse'' to an ''HttpResponse'' with http-status
   * copied with JSON's field tagged with ''statusTag''.
   *
   * @param statusTag the status tag identifier
   */
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
   * A REST API resource that primary defines a ''route''.
   */
  trait RestResource { self =>

    /**
     * @return a route of this resource
     */
    def route: PartialFunction[(HttpMethod, Path), Service[HttpRequest, HttpResponse]]

    /**
     * Combines ''this'' resource with ''that'' resource. A new resource
     * contains routes of both ''this'' and ''that'' resources.
     *
     * @param that the resource to be combined with
     *
     * @return a new resource
     */
    def orElse(that: RestResource) = new RestResource {
      def route = self.route orElse that.route
    }

    /**
     * Applies given function ''fn'' to every route's endpoints of this resource.
     *
     * @param fn the function to be applied
     *
     * @return a new resource
     */
    def andThen(fn: Service[HttpRequest, HttpResponse] => Service[HttpRequest, HttpResponse]) =
      new RestResource {
        def route = self.route andThen fn
      }

    private[this] implicit class AfterThatService[RepIn](service: Service[HttpRequest, RepIn]) {
      def afterThat[A](thatFacet: Facet[RepIn, A]) =
        thatFacet andThen service
    }

    private[this] implicit class AfterThatFacet[RepIn, RepOut](facet: Facet[RepIn, RepOut]) {
      def afterThat[A](thatFacet: Facet[RepOut, A]) =
        thatFacet andThen facet
    }
  }

  /**
   * A base class for ''RestApi'' backend.
   */
  class RestApi extends App {

    private[this] implicit class FilterAndThenResource(filter: Filter[HttpRequest, HttpResponse, HttpRequest, HttpResponse]) {
      def andThen(resource: => RestResource) = resource andThen { filter andThen _ }
    }

    /**
     * @return a name of this Finch instance
     */
    def name = "FinchInstance-" + new Random().alphanumeric.take(20)

    /**
     * Exposes given ''resource'' at specified ''port'' and serves the requests.
     *
     * @param port the socket port number to listen
     * @param resource the resource to expose
     *
     * @return nothing
     */
    def exposeAt(port: Int)(resource: => RestResource): Unit = {

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
