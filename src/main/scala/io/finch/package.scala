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
import com.twitter.finagle.{Filter, Service}
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.path.Path
import com.twitter.finagle.builder.ServerBuilder
import scala.util.parsing.json.{JSONFormat, JSONType, JSONArray, JSONObject}
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
   * Alters any object with ''toFuture'' method.
   *
   * @param any an object to be altered
   * @tparam A an object type
   */
  implicit class _AnyToFuture[A](val any: A) extends AnyVal {
    def toFuture: Future[A] = Future.value(any)
  }

  implicit class _FilterAndThen[RepIn, RepOut](
      val filter: Filter[HttpRequest, RepOut, HttpRequest, RepIn]) extends AnyVal{

    def andThen(thatResource: RestResourceOf[RepIn]) =
      thatResource andThen { filter andThen _ }
  }

  implicit class _ServiceAfterThat[RepIn](
      val service: Service[HttpRequest, RepIn]) extends AnyVal {

    def afterThat[RepOut](thatFilter: Filter[HttpRequest, RepOut, HttpRequest, RepIn]) =
      thatFilter andThen service
  }

  implicit class _JsonObjectOps(val json: JSONObject) extends AnyVal {
    def flatMapTagInFuture[A](tag: String)(fn: A => Future[Any]) = json.obj.get(tag) match {
      case Some(any) => fn(any.asInstanceOf[A]) flatMap { a =>
        json.copy(Map(tag -> a)).toFuture
      }
      case None => json.toFuture
    }

    def mapTagInFuture[A](tag: String)(fn: A => Any) = mapTag[A](tag)(fn).toFuture

    def mapTag[A](tag: String)(fn: A => Any) = json.obj.get(tag) match {
      case Some(any) => json.copy(Map(tag -> fn(any.asInstanceOf[A])))
      case None => json
    }

    def apply[A](tag: String) = json.obj(tag).asInstanceOf[A]

    def get[A](tag: String) = apply[A](tag)
    def getOrElse[A](tag: String, default: => A) = json.obj.getOrElse(tag, default).asInstanceOf[A]
  }

  implicit class _JsonArrayOps(val json: JSONArray) extends AnyVal {
    def flatMapInFuture(fn: JSONObject => Future[Any]) = {
      val fs = json.list map { a => fn(a.asInstanceOf[JSONObject]) }
      Future.collect(fs) flatMap { seq => JsonArray(seq).toFuture }
    }

    def mapInFuture(fn: JSONObject => Any) = map(fn).toFuture

    def map(fn: JSONObject => Any) =
      json.list map { a => fn(a.asInstanceOf[JSONObject]) }
  }

  /**
   * An HttpService with specified response type.
   *
   * @tparam Rep the response type
   */
  trait HttpServiceOf[+Rep] extends Service[HttpRequest, Rep]

  /**
   * A pure HttpService.
   */
  trait HttpService extends HttpServiceOf[HttpResponse]

  /**
   * A Facet that has a ''req'' available.
   *
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait FacetWithRequest[-RepIn, +RepOut] extends Filter[HttpRequest, RepOut, HttpRequest, RepIn] {

    /**
     * Converts given pair ''req'' and ''rep'' of type ''RepIn'' to type ''RepOut''.
     *
     * @param req the request
     * @param rep the response to convert
     *
     * @return a converted response
     */
    def apply(req: HttpRequest)(rep: RepIn): Future[RepOut]

    def apply(req: HttpRequest, service: Service[HttpRequest, RepIn]) =
      service(req) flatMap apply(req)
  }

  /**
   * Facet implements Filter interface but has a different meaning. Facets are
   * converts services responses from ''RepIn'' to ''RepOut''.
   *
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait Facet[-RepIn, +RepOut] extends FacetWithRequest[RepIn, RepOut] {

    /**
     * Converts given ''rep'' from ''RepIn'' to ''RepOut'' type.
     *
     * @param rep the response to convert
     *
     * @return a converted response
     */
    def apply(rep: RepIn): Future[RepOut]

    def apply(req: HttpRequest)(rep: RepIn) = apply(rep)
  }

  /**
   * An HTTP filter that just filters the ''HttpRequest''-s.
   *
   * @tparam Rep the response type
   */
  trait HttpFilterOf[Rep] extends Filter[HttpRequest, Rep, HttpRequest, Rep]

  /**
   * An HTTP filter typed with ''HttpResponse''.
   */
  trait HttpFilter extends HttpFilterOf[HttpResponse]

  object JsonObject {
    def apply(args: (String, Any)*) = JSONObject(args.toMap)
    def apply(map: Map[String, Any]) = JSONObject(map)
    def empty = JSONObject(Map.empty[String, Any])
    def unapply(outer: Any): Option[JSONObject] = outer match {
      case inner: JSONObject => Some(inner)
      case _ => None
    }
  }

  object JsonArray {
    def apply(args: Any*) = JSONArray(args.toList)
    def apply(list: List[Any]) = JSONArray(list)
    def empty = JSONArray(List.empty[Any])
    def unapply(outer: Any): Option[JSONArray] = outer match {
      case inner: JSONArray => Some(inner)
      case _ => None
    }
  }

  trait JsonFormatter extends JSONFormat.ValueFormatter { self =>
    def apply(x: Any) = x match {
      case s: String => "\"" + formatString(s) + "\""
      case o: JSONObject => o.toString(self)
      case a: JSONArray => a.toString(self)
      case other => other.toString
    }

    def formatString(s: String) =
      s flatMap { c => escapeOrSkip(c) }

    def escapeOrSkip: PartialFunction[Char, String] = escapeChar orElse {
      case c => c.toString
    }

    def escapeChar: PartialFunction[Char, String]
  }

  private[this] object DefaultJsonFormatter extends JsonFormatter {
    def escapeChar = {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
    }
  }

  /**
   * A facet that turns a ''JsonResponse'' to an ''HttpResponse''.
   */
  object TurnJsonIntoHttp extends Facet[JsonResponse, HttpResponse] {
    def apply(rep: JsonResponse) = {
      val reply = Response(Version.Http11, Status.Ok)
      reply.setContentTypeJson()
      reply.setContentString(rep.toString(DefaultJsonFormatter))

      reply.toFuture
    }
  }

  /**
   * A facet that turns a ''JsonResponse'' to an ''HttpResponse'' with http-status
   * copied with JSON's field tagged with ''statusTag''.
   *
   * @param statusTag the status tag identifier
   */
  class TurnJsonIntoHttpWithStatusFromTag(statusTag: String) extends Facet[JsonResponse, HttpResponse] {
    def apply(rep: JsonResponse) = {
      val status = rep match {
        case JsonObject(o) =>
          HttpResponseStatus.valueOf(o.getOrElse[Int](statusTag, 200))
        case _ => Status.Ok
      }

      val reply = Response(Version.Http11, status)
      reply.setContentTypeJson()
      reply.setContentString(rep.toString(DefaultJsonFormatter))

      reply.toFuture
    }
  }

  /**
   * A REST API resource that primary defines a ''route''.
   */
  trait RestResourceOf[Rep] { self =>

    /**
     * @return a route of this resource
     */
    def route: PartialFunction[(HttpMethod, Path), Service[HttpRequest, Rep]]

    /**
     * Combines ''this'' resource with ''that'' resource. A new resource
     * contains routes of both ''this'' and ''that'' resources.
     *
     * @param that the resource to be combined with
     *
     * @return a new resource
     */
    def orElse(that: RestResourceOf[Rep]) = new RestResourceOf[Rep] {
      def route = self.route orElse that.route
    }

    /**
     * Applies given function ''fn'' to every route's endpoints of this resource.
     *
     * @param fn the function to be applied
     *
     * @return a new resource
     */
    def andThen[RepOut](fn: Service[HttpRequest, Rep] => Service[HttpRequest, RepOut]) =
      new RestResourceOf[RepOut] {
        def route = self.route andThen fn
      }

    /**
     * Applies given ''filter'' to this resource.
     *
     * @param filter a filter to apply
     * @tparam RepOut a response type of new resource
     *
     * @return a new resource
     */
    def afterThat[RepOut](filter: Filter[HttpRequest, RepOut, HttpRequest, Rep]) =
      andThen { filter andThen _ }
  }

  /**
   * A default REST resource.
   */
  trait RestResource extends RestResourceOf[HttpResponse]

  /**
   * A base class for ''RestApi'' backend.
   */
  abstract class RestApiOf[Rep] extends App {

    /**
     * @return a resource of this API
     */
    def resource: RestResourceOf[Rep]

    /**
     * Loopbacks given ''HttpRequest'' to a resource.
     *
     * @param req the ''HttpRequest'' to loopback
     * @return a response wrapped with ''Future''
     */
    def apply(req: HttpRequest): Future[Rep] =
      resource.route(req.method -> Path(req.path))(req)

    /**
     * Loopbacks given request (represented by a URI string) to a resource
     *
     * @param uri the uri to loopback
     * @return a response wrapped with ''Future''
     */
    def apply(uri: String): Future[Rep] = apply(Request(uri))

    /**
     * @return a name of this Finch instance
     */
    def name = "FinchInstance-" + new Random().alphanumeric.take(20).mkString

    /**
     * Exposes given ''resource'' at specified ''port'' and serves the requests.
     *
     * @param port the socket port number to listen
     * @param fn the function that transforms a resource type to ''HttpResponse''
     */
    def exposeAt(port: Int)(fn: RestResourceOf[Rep] => RestResourceOf[HttpResponse]): Unit = {

      val httpResource = fn(resource)

      val service = new RoutingService[HttpRequest](
        new PartialFunction[HttpRequest, Service[HttpRequest, HttpResponse]] {
          def apply(req: HttpRequest) = httpResource.route(req.method -> Path(req.path))
          def isDefinedAt(req: HttpRequest) = httpResource.route.isDefinedAt(req.method -> Path(req.path))
        })

      ServerBuilder()
        .codec(RichHttp[HttpRequest](Http()))
        .bindTo(new InetSocketAddress(port))
        .name(name)
        .build(service)
    }
  }

  /**
   * A default REST API backend.
   */
  abstract class RestApi extends RestApiOf[HttpResponse]
}
