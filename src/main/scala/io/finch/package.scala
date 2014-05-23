/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
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
 *      (just a special case of Finagle's ''Filter'')
 *   3. ''ResourceOf[A]'' that provides route information about a particular resource
 *      (just a special case of ''PartialFunction'' from route to ''HttpService'')
 *   4. ''RestApiOf[A]'' that aggregates all the things together: resources and a set
 *      of rules (exposed as a combination of facets and filters) that transform the
 *      ''HttpResourceOf[A]'' to a ''HttpResource''.
 *
 * I'm trying to follow the principles of my elder brother and keep the things
 * as composable as possible.
 *
 *   (a) In order to mark the difference between filters and facets and show the
 *       direction of a data-flow, the facets are composed by ''afterThat'' operator
 *       within a reversed order:
 *
 *        '''val s = service afterThat facetA afterThat facetB'''

 *   (b) Resources might be treated as partial functions, so they may be composed
 *       together with ''orElse'' operator:
 *
 *        '''val r = resourceA orElse resourceB'''

 *   (c) Another useful resource operator is ''andThen'' that takes a function from
 *       ''HttpService'' to ''HttpService'' and returns a new resource within function
 *       applied to its every route endpoint.
 *
 *        '''val r = resource andThen { filter andThen _ }'''
 *
 *   (d) Resources may also be composed with filters by using the ''afterThat'' operator
 *       in a familiar way:
 *
 *        '''val r = authorize afterThat resource'''
 *
 *   (e) Primitive filters (that don't change anything) are composed with the same
 *       ''afterThat'' operator:
 *
 *        '''val f = filterA afterThat filterB afterThat filterC'''
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
   * Alters any object within a ''toFuture'' method.
   *
   * @param any an object to be altered
   *
   * @tparam A an object type
   */
  implicit class _AnyToFuture[A](val any: A) extends AnyVal {

    /**
     * Converts this ''any'' object into a ''Future''
     *
     * @return an object wrapped with ''Future''
     */
    def toFuture: Future[A] = Future.value(any)
  }

  /**
   * Alters underlying filter within ''afterThat'' methods composing a filter
   * with a given resource or withing a next filter.
   *
   * @param filter a filter to be altered
   */
  implicit class _FilterAndThen[ReqIn <: HttpRequest, ReqOut <: HttpRequest, RepIn, RepOut](
      val filter: Filter[ReqIn, RepOut, ReqOut, RepIn]) extends AnyVal {

    /**
     * Composes this filter within a given resource ''thatResource''.
     *
     * @param thatResource a resource to compose
     *
     * @return a resource composed with filter
     */
    def andThen(thatResource: RestResource[ReqOut, RepIn]) =
      thatResource andThen { service =>
        filter andThen service
      }
  }

  /**
   * Alters underlying service within ''afterThat'' method composing a service
   * with a given filter.
   *
   * @param service a service to be altered
   *
   * @tparam RepIn a input response type
   */
  implicit class _ServiceAfterThat[Req <: HttpRequest, RepIn](service: Service[Req, RepIn]) {

    /**
     * Composes this service with a given filter ''thatFilter''.
     *
     * @param facet a facet to compose
     * @tparam RepOut an output response type
     *
     * @return a new service composed with a filter
     */
    def afterThat[RepOut](facet: Facet[RepIn, RepOut]) =
      new Service[Req, RepOut] {
        def apply(req: Req) = service(req) flatMap { rep => facet(rep) }
      }
  }

  /**
   * Alters underlying json object within finagled methods.
   *
   * @param json a json object to be altered
   */
  implicit class _JsonObjectOps(val json: JSONObject) extends AnyVal {

    /**
     * Maps this json object into a future with given ''tag'' updated
     * via async function ''fn''.
     *
     * @param tag a tag to update
     * @param fn an async function to transform the tag
     * @tparam A a value type associated with a given tag
     *
     * @return a future of a new json object with tag updated
     */
    def flatMapTagInFuture[A](tag: String)(fn: A => Future[Any]) = json.obj.get(tag) match {
      case Some(any) => fn(any.asInstanceOf[A]) flatMap { a =>
        JSONObject(json.obj + (tag -> a)).toFuture
      }
      case None => json.toFuture
    }

    /**
     * Maps this json object into a future with given ''tag'' updated
     * via pure function ''fn''.
     *
     * @param tag a tag to update
     * @param fn a pure function to transform the tag
     * @tparam A a value type associated with a given tag
     *
     * @return a future of a new json object with tag updated
     */
    def mapTagInFuture[A](tag: String)(fn: A => Any) = mapTag[A](tag)(fn).toFuture

    /**
     * Maps this json object with given ''tag'' updated via pure function ''fn''.
     *
     * @param tag a tag to update
     * @param fn a pure function to transform the tag
     * @tparam A a value type associated with a given tag
     *
     * @return a json object with tag updated
     */
    def mapTag[A](tag: String)(fn: A => Any) = json.obj.get(tag) match {
      case Some(any) => JSONObject(json.obj + (tag -> fn(any.asInstanceOf[A])))
      case None => json
    }

    /**
     * Retrieves the string value associated with a given ''tag'' in this json
     * object.
     *
     * @param tag a tag
     *
     * @return a value associated with a tag
     */
    def apply(tag: String) = get[String](tag)

    /**
     * Retrieves the typed ''A'' value associated with a given ''tag'' in this
     * json object
     *
     * @param tag a tag
     * @tparam A a value type
     *
     * @return a value associated with a tag
     */
    def get[A](tag: String) = json.obj(tag).asInstanceOf[A]

    /**
     * Retrieves the typed ''A'' value associated with a given ''tag'' in this
     * json object or ''default'' value if the tag doesn't exist.
     *
     * @param tag a tag
     * @param default a default value
     * @tparam A a value type
     *
     * @return a value associated with a tag or default value
     */
    def getOrElse[A](tag: String, default: => A) = json.obj.getOrElse(tag, default).asInstanceOf[A]

    /**
     * Retrieves the typed ''A'' option of a value associated with a given ''tag''
     * in this json object.
     *
     * @param tag a tag
     * @tparam A a value type
     *
     * @return an option of a value associated with a tag
     */
    def getOption[A](tag: String) = json.obj.get(tag) match {
      case Some(a) => Some(a.asInstanceOf[A])
      case None => None
    }
  }

  /**
   * Alters underlying json array within finagled methods.
   *
   * @param json a json array to alter
   */
  implicit class _JsonArrayOps(val json: JSONArray) extends AnyVal {

    /**
     * Maps this json array into a future with all the items mapped via
     * async function ''fn''.
     *
     * @param fn an async function to map items
     *
     * @return a future of json array with items mapped
     */
    def flatMapInFuture(fn: JsonResponse => Future[JsonResponse]) = {
      val fs = json.list map { a => fn(a.asInstanceOf[JsonResponse]) }
      Future.collect(fs) flatMap { seq => JsonArray(seq).toFuture }
    }

    /**
     * Maps this json array into a future with all the items mapped
     * via pure function ''fn''.
     *
     * @param fn a pure function to map items
     *
     * @return a future of json array with items mapped
     */
    def mapInFuture(fn: JsonResponse => JsonResponse) = map(fn).toFuture

    /**
     * Maps this json array into a json array with all the items mapped
     * via pure function ''fn''.
     *
     * @param fn a pure function to map items
     *
     * @return a json array with items mapped
     */
    def map(fn: JsonResponse => JsonResponse) =
      JsonArray(json.list map { a => fn(a.asInstanceOf[JsonResponse]) })
  }

  /**
   * An ''HttpService'' with specified response type ''Rep''.
   *
   * @tparam Rep the response type
   */
  trait HttpServiceOf[+Rep] extends Service[HttpRequest, Rep]

  /**
   * A pure ''HttpService''.
   */
  trait HttpService extends HttpServiceOf[HttpResponse]

  /**
   * A ''Facet'' that has a request available.
   *
   *  TODO: Suport this.
   *
   * @tparam Req the request type
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait FacetWithRequest[Req <: HttpRequest, -RepIn, +RepOut] {

    /**
     * Converts given pair ''req'' and ''rep'' of type ''RepIn'' to type ''RepOut''.
     *
     * @param req the request
     * @param rep the response to convert
     *
     * @return a converted response
     */
    def apply(req: Req)(rep: RepIn): Future[RepOut]
  }

  /**
   * Facet implements Filter interface but has a different meaning. Facets are
   * converts services responses from ''RepIn'' to ''RepOut''.
   *
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait Facet[-RepIn, +RepOut] {

    /**
     * Converts given ''rep'' from ''RepIn'' to ''RepOut'' type.
     *
     * @param rep the response to convert
     *
     * @return a converted response
     */
    def apply(rep: RepIn): Future[RepOut]
  }

  object JsonObject {
    def apply(args: (String, Any)*) = JSONObject(args.toMap)
    def empty = JSONObject(Map.empty[String, Any])
    def unapply(outer: Any): Option[JSONObject] = outer match {
      case inner: JSONObject => Some(inner)
      case _ => None
    }
  }

  /**
   * A companion object for ''JSONArray''.
   */
  object JsonArray {
    def apply(seq: Seq[JsonResponse]) = JSONArray(seq.toList)
    def empty = JSONArray(List.empty[JsonResponse])
    def unapply(outer: Any): Option[JSONArray] = outer match {
      case inner: JSONArray => Some(inner)
      case _ => None
    }
  }

  /**
   * A json formatter that primary escape special chars.
   */
  trait JsonFormatter extends JSONFormat.ValueFormatter { self =>
    def apply(x: Any) = x match {
      case s: String => "\"" + formatString(s) + "\""
      case o: JSONObject => o.toString(self)
      case a: JSONArray => a.toString(self)
      case other => other.toString
    }

    def formatString(s: String) = s flatMap { escapeOrSkip(_) }

    def escapeOrSkip: PartialFunction[Char, String] = escapeChar orElse {
      case c => c.toString
    }

    /**
     * A partial function that defines a set of rules on how to escape the
     * special characters in a string.
     *
     * @return an escaped char represented as a string
     */
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
   * A facet that turns a ''JsonResponse'' into an ''HttpResponse''.
   */
  class TurnJsonIntoHttpWithFormatter(formatter: JsonFormatter = DefaultJsonFormatter)
      extends Facet[JsonResponse, HttpResponse] {

    def apply(rep: JsonResponse) = {
      val reply = Response(Version.Http11, Status.Ok)
      reply.setContentTypeJson()
      reply.setContentString(rep.toString(formatter))

      reply.toFuture
    }
  }

  /**
   * A facet that turns a ''JsonResponse'' into an ''HttpResponse''.
   */
  object TurnJsonIntoHttp extends TurnJsonIntoHttpWithFormatter

  /**
   * A facet that turns a ''JsonResponse'' into an ''HttpResponse'' with http status.
   *
   * @param statusTag the status tag identifier
   */
  class TurnJsonIntoHttpWithStatusFromTag(
    statusTag: String = "status",
    formatter: JsonFormatter = DefaultJsonFormatter) extends Facet[JsonResponse, HttpResponse] {

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
   * A facet that turns a ''JsonResponse'' to an ''HttpResponse'' with http status.
   */
  object TurnJsonIntoHttpWithStatus extends TurnJsonIntoHttpWithStatusFromTag

  /**
   * A REST API resource that primary defines a ''route''.
   *
   * @tparam Rep a response type
   */
  trait RestResource[Req <: HttpRequest, Rep] { self =>

    /**
     * @return a route of this resource
     */
    def route: PartialFunction[(HttpMethod, Path), Service[Req, Rep]]

    /**
     * Combines ''this'' resource with ''that'' resource. A new resource
     * contains routes of both ''this'' and ''that'' resources.
     *
     * @param that the resource to be combined with
     *
     * @return a new resource
     */
    def orElse(that: RestResource[Req, Rep]) = new RestResource[Req, Rep] {
      def route = self.route orElse that.route
    }

    /**
     * Applies given function ''fn'' to every route's endpoints of this resource.
     *
     * @param fn the function to be applied
     *
     * @return a new resource
     */
    def andThen[ReqOut <: HttpRequest, RepOut](fn: Service[Req, Rep] => Service[ReqOut, RepOut]) =
      new RestResource[ReqOut, RepOut] {
        def route = self.route andThen fn
      }

    /**
     * Applies given ''filter'' to this resource.
     *
     * @param facet a filter to apply
     * @tparam RepOut a response type of new resource
     *
     * @return a new resource
     */
    def afterThat[RepOut](facet: Facet[Rep, RepOut]) = andThen { service =>
      new Service[Req, RepOut] {
        def apply(req: Req) = service(req) flatMap { rep => facet(rep) }
      }
    }
  }

  /**
   * A default REST resource.
   */
  trait RestResourceOf[Rep] extends RestResource[HttpRequest, Rep]

  /**
   * A base class for ''RestApi'' backend.
   */
  abstract class RestApi[Req <: HttpRequest, Rep] extends App {

    /**
     * @return a resource of this API
     */
    def resource: RestResource[Req, Rep]

    /**
     * Sends a request ''req'' to this API.

     * @param req the request to send
     * @return a response wrapped with ''Future''
     */
    def apply(req: Req): Future[Rep] =
      resource.route(req.method -> Path(req.path))(req)

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
    def exposeAt(port: Int)(fn: RestResource[Req, Rep] => RestResource[HttpRequest, HttpResponse]): Unit = {

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
  abstract class RestApiOf[Rep] extends RestApi[HttpRequest, Rep]
}
