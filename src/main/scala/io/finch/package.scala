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
import scala.util.parsing.json.{JSONFormat, JSONType, JSONArray, JSONObject}
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpMethod}
import com.twitter.finagle.http.{Status, Version, Response, Request}

/***
 * Hi! I'm Finch - a super-tiny library atop of Finagle that makes the
 * development of RESTFul API services more pleasant and slick.
 *
 * I'm built around three very simple building-blocks:
 *   1. ''HttpServiceOf[A]'' that maps ''HttpRequest'' to some response
 *      (both are just a special cases of Finagle's ''Service'')
 *   2. ''Facet[+A, -B]'' that transforms service's response ''A'' to ''B''
 *      (just a special case of Finagle's ''Filter'')
 *   3. ''EndpointOf[A]'' that provides route information about a particular endpoint
 *      (just a special case of ''PartialFunction'' from route to ''HttpService'')
 *   4. ''ApiOf[A]'' that aggregates all the things together: endpoints and a set
 *      of rules (exposed as a combination of facets and filters) that transform the
 *      ''EndpointOf[A]'' to a ''Endpoint''.
 *
 * I'm trying to follow the principles of my elder brother and keep the things
 * as composable as possible.
 *
 *   (a) In order to mark the difference between filters and facets and show the
 *       direction of a data-flow, the facets are composed by ''afterThat'' operator
 *       within a reversed order:
 *
 *        '''val s = service afterThat facetA afterThat facetB'''

 *   (b) Endpoints might be treated as partial functions, so they may be composed
 *       together with ''orElse'' operator:
 *
 *        '''val r = endpointA orElse endpointB'''

 *   (c) Another useful endpoint operator is ''andThen'' that takes a function from
 *       ''HttpService'' to ''HttpService'' and returns a new endpoint within function
 *       applied to its every route endpoint.
 *
 *        '''val r = endpoint andThen { filter andThen _ }'''
 *
 *   (d) Endpoints may also be composed with filters by using the ''afterThat'' operator
 *       in a familiar way:
 *
 *        '''val r = authorize afterThat endpoint'''
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
  implicit class AnyOps[A](val any: A) extends AnyVal {

    /**
     * Converts this ''any'' object into a ''Future''
     *
     * @return an object wrapped with ''Future''
     */
    def toFuture: Future[A] = Future.value(any)
  }

  /**
   * Alters any throwable with a ''toFutureException'' method.
   *
   * @param t a throwable to be altered
   */
  implicit class ThrowableOps(val t: Throwable) extends AnyVal {

    /**
     * Converts this throwable object into a ''Future'' exception.
     *
     * @return an exception wrapped with ''Future''
     */
    def toFutureException[A]: Future[A] = Future.exception(t)
  }

  /**
   * Alters underlying filter within ''afterThat'' methods composing a filter
   * with a given endpoint or withing a next filter.
   *
   * @param filter a filter to be altered
   */
  implicit class FilterOps[ReqIn <: HttpRequest, ReqOut <: HttpRequest, RepIn, RepOut](
      val filter: Filter[ReqIn, RepOut, ReqOut, RepIn]) extends AnyVal {

    /**
     * Composes this filter within a given endpoint ''thatEndpoint''.
     *
     * @param endpoint an endpoint to compose
     *
     * @return an endpoint composed with filter
     */
    def andThen(endpoint: Endpoint[ReqOut, RepIn]) =
      endpoint andThen { service =>
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
  implicit class ServiceOps[Req <: HttpRequest, RepIn](service: Service[Req, RepIn]) {

    /**
     * Composes this service with a given facet-with-request ''facet''.
     *
     * @param facet a facet to compose
     * @tparam RepOut an output response type
     *
     * @return a new service composed with facet.
     */
    def afterThat[ReqIn >: Req <: HttpRequest, RepOut](facet: FacetWithRequest[ReqIn, RepIn, RepOut]) =
      new Service[Req, RepOut] {
        def apply(req: Req) = service(req) flatMap { facet(req)(_) }
      }
  }

  /**
   * Alters underlying json object within finagled methods.
   *
   * @param json a json object to be altered
   */
  implicit class JsonObjectOps(val json: JSONObject) extends AnyVal {

    /**
     * Retrieves the typed ''A'' value associated with a given ''tag'' in this
     * json object
     *
     * @param path a tag
     * @tparam A a value type
     *
     * @return a value associated with a tag
     */
    def get[A](path: String) = getOption[A](path).get

    /**
     * Retrieves the typed ''A'' option of a value associated with a given ''tag''
     * in this json object.
     *
     * @param path a path
     * @tparam A a value type
     *
     * @return an option of a value associated with a tag
     */
    def getOption[A](path: String) = {
      def loop(path: List[String], j: JSONObject): Option[A] = path match {
        case tag :: Nil => j.obj.get(tag) map { _.asInstanceOf[A] }
        case tag :: tail => j.obj.get(tag) match {
          case Some(jj: JSONObject) => loop(tail, jj)
          case _ => None
        }
      }

      loop(path.split('.').toList, json)
    }

    /**
     * Maps this json object into a json object with underlying ''map'' mapped
     * via pure function ''fn''.
     *
     * @param fn a pure function to map map
     *
     * @return a json object
     */
    def within(fn: Map[String, Any] => Map[String, Any]) = JSONObject(fn(json.obj))

    /**
     * Removes all null-value properties from this json object.
     *
     * @return a compacted json object
     */
    def compacted = {
      def loop(obj: Map[String, Any]): Map[String, Any] = obj.flatMap {
        case (t, JsonNull) => Map.empty[String, Any]
        case (tag, j: JSONObject) =>
          val o = loop(j.obj)
          if (o.isEmpty) Map.empty[String, Any]
          else Map(tag -> JSONObject(o))
        case (tag, value) => Map(tag -> value)
      }

      JSONObject(loop(json.obj))
    }
  }

  /**
   * Alters underlying json array within finagled methods.
   *
   * @param json a json array to be alter
   */
  implicit class JsonArrayOps(val json: JSONArray) extends AnyVal {

    /**
     * Maps this json array into a json array with underlying ''list'' mapped
     * via pure function ''fn''.
     *
     * @param fn a pure function to map list
     *
     * @return a json array
     */
    def within(fn: List[Any] => List[Any]) = JSONArray(fn(json.list))
  }

  /**
   * A ''Facet'' that has a request available in it.
   *
   * @tparam Req the request type
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait FacetWithRequest[Req <: HttpRequest, -RepIn, +RepOut] { self =>

    /**
     * Converts given pair ''req'' and ''rep'' of type ''RepIn'' to type ''RepOut''.
     *
     * @param req the request
     * @param rep the response to convert
     *
     * @return a converted response
     */
    def apply(req: Req)(rep: RepIn): Future[RepOut]

    /**
     * Composes this facet-with-request with given ''next'' facet.
     *
     * @param next the facet to compose with
     * @tparam Rep the response type
     *
     * @return a composed facet-with-request
     */
    def afterThat[ReqIn >: Req <: HttpRequest, Rep](next: FacetWithRequest[ReqIn, RepOut, Rep]) =
      new FacetWithRequest[Req, RepIn, Rep] {
        def apply(req: Req)(rep: RepIn) = self(req)(rep) flatMap { next(req)(_) }
      }
  }

  /**
   * Facets are converts services responses from ''RepIn'' to ''RepOut''
   * regarding what is the request type.
   *
   * @tparam RepIn the input response type
   * @tparam RepOut the output response type
   */
  trait Facet[-RepIn, +RepOut] extends FacetWithRequest[HttpRequest, RepIn, RepOut] {

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
   * Intrusive json null value.
   */
  object JsonNull {
    override def toString = null
  }

  /**
   * A companion object for json object.
   */
  object JsonObject {

    /**
     * Creates a json object of given sequence of properties ''args''. Every
     * argument/property is a pair of ''tag'' and ''value'' associated with it.
     * It's possible to pass a complete json path (separated by dot) as ''tag''.
     *
     * @param args a sequence of json properties
     *
     * @return a json object
     */
    def apply(args: (String, Any)*) = {
      def loop(path: List[String], value: Any): Map[String, Any] = path match {
        case tag :: Nil => Map(tag -> value)
        case tag :: tail => Map(tag -> JSONObject(loop(tail, value)))
      }

      val jsonSeq = args.flatMap {
        case (path, value) =>
          Seq(JSONObject(loop(path.split('.').toList, if (value == null) JsonNull else value)))
      }

      jsonSeq.foldLeft(JsonObject.empty) { mergeRight }
    }

    /**
     * Creates an empty json object
     *
     * @return an empty json object
     */
    def empty = JSONObject(Map.empty[String, Any])

    def unapply(outer: Any): Option[JSONObject] = outer match {
      case inner: JSONObject => Some(inner)
      case _ => None
    }

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a right json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeRight(a: JSONObject, b: JSONObject) = mergeLeft(b, a)

    /**
     * Deeply merges given json objects ''a'' and ''b'' into a single json object.
     * In case of conflict tag the value of a left json object will be taken.
     *
     * @param a the left json object
     * @param b the right json object
     *
     * @return a merged json object
     */
    def mergeLeft(a: JSONObject, b: JSONObject): JSONObject = {
      def loop(aa: Map[String, Any], bb: Map[String, Any]): Map[String, Any] =
        if (aa.isEmpty) bb
        else if (bb.isEmpty) aa
        else {
          val (tag, value) = aa.head
          if (!bb.contains(tag)) loop(aa.tail, bb + (tag -> value))
          else (value, bb(tag)) match {
            case (ja: JSONObject, jb: JSONObject) =>
              loop(aa.tail, bb + (tag -> JSONObject(loop(ja.obj, jb.obj))))
            case (_, _) => loop(aa.tail, bb + (tag -> value))
          }
        }

      JSONObject(loop(a.obj, b.obj))
    }
  }

  /**
   * A companion object for json array.
   */
  object JsonArray {

    /**
     * Creates a new json array of given sequence of items ''args''.
     *
     * @param args sequence of items in the array
     *
     * @return a new json object
     */
    def apply(args: Any*) = JSONArray(args.toList)

    /**
     * Creates an empty json array.
     *
     * @return an empty json array.
     */
    def empty = JSONArray(List.empty[Any])

    def unapply(outer: Any): Option[JSONArray] = outer match {
      case inner: JSONArray => Some(inner)
      case _ => None
    }

    /**
     * Concatenates two given arrays ''a'' and ''b''.
     *
     * @param a the left array
     * @param b the right array
     *
     * @return a concatenated array
     */
    def concat(a: JSONArray, b: JSONArray) = JSONArray(a.list ::: b.list)
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

  /**
   * A default json formatter that doesn't escape forward slashes.
   */
  object DefaultJsonFormatter extends JsonFormatter {
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
          HttpResponseStatus.valueOf(o.getOption[Int](statusTag).getOrElse(200))
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
   * A REST API endpoint that primary defines a ''route'' and might be converted
   * into a finagled service with ''toService'' method.
   *
   * @tparam Rep a response type
   */
  trait Endpoint[Req <: HttpRequest, Rep] { self =>

    /**
     * A rich route of this endpoint.
     *
     * @return a route of this endpoint
     */
    def route: PartialFunction[(HttpMethod, Path), Service[Req, Rep]]

    /**
     * Sends a request ''req'' to this Endpoint.
     *
     * @param req the request to send
     * @return a response wrapped with ''Future''
     */
    def apply(req: Req): Future[Rep] = route(req.method -> Path(req.path))(req)

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
    def orElse(that: PartialFunction[(HttpMethod, Path), Service[Req, Rep]]): Endpoint[Req, Rep] =
      new Endpoint[Req, Rep] {
        def route = self.route orElse that
      }

    /**
     * Applies given function ''fn'' to every route's endpoints of this endpoint.
     *
     * @param fn the function to be applied
     *
     * @return a new endpoint
     */
    def andThen[ReqOut <: HttpRequest, RepOut](fn: Service[Req, Rep] => Service[ReqOut, RepOut]) =
      new Endpoint[ReqOut, RepOut] {
        def route = self.route andThen fn
      }

    /**
     * Applies given ''facet'' to this endpoint.
     *
     * @param facet a facet to apply
     * @tparam RepOut a response type of a new endpoint
     *
     * @return a new endpoint
     */
    def afterThat[ReqIn >: Req <: HttpRequest, RepOut](facet: FacetWithRequest[ReqIn, Rep, RepOut]) =
      andThen { service =>
        new Service[Req, RepOut] {
          def apply(req: Req) = service(req) flatMap { facet(req)(_) }
        }
      }

    /**
     * Converts this endpoint into a finagled service.
     *
     * @param setup the function that transforms an endpoint's type to ''HttpResponse''
     *
     * @return a finagled service
     */
    def toService(implicit setup: Endpoint[Req, Rep] => Endpoint[HttpRequest, HttpResponse]) = {
      val httpEndpoint = setup(self)

      new RoutingService[HttpRequest](
        new PartialFunction[HttpRequest, Service[HttpRequest, HttpResponse]] {
          def apply(req: HttpRequest) = httpEndpoint.route(req.method -> Path(req.path))
          def isDefinedAt(req: HttpRequest) = httpEndpoint.route.isDefinedAt(req.method -> Path(req.path))
        })
    }
  }

  /**
   * A companion object for ''Endpoint''
   */
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
    def join[Req <: HttpRequest, Rep](endpoints: Endpoint[Req, Rep]*) = endpoints.reduce(_ orElse _)
  }

  /**
   * A base exception of request reader.
   *
   * @param m the message
   */
  class RequestReaderError(m: String) extends Exception(m)

  /**
   * An exception that indicates missed parameter in the request.
   *
   * @param param the missed parameter name
   */
  class ParamNotFound(param: String) extends RequestReaderError("Param '" + param + "' not found in the request.")

  /**
   * An exception that indicates a broken validation rule on the request.
   *
   * @param rule the rule description
   */
  class ValidationFailed(rule: String) extends RequestReaderError("Request validation failed: '" + rule + "'.")

  /**
   * A request reader (implementing reader monad pattern) that reads something
   * of type ''A'' from the ''HttpRequest'' into a ''Future''.
   *
   * @tparam A the result type
   */
  trait FutureRequestReader[A] { self =>

    /**
     * Reads the data from given ''req''.
     *
     * @param req the request to read
     */
    def apply(req: HttpRequest): Future[A]

    def flatMap[B](fn: A => FutureRequestReader[B]) = new FutureRequestReader[B] {
      def apply(req: HttpRequest) = self(req) flatMap { fn(_)(req) }
    }

    def map[B](fn: A => B) = new FutureRequestReader[B] {
      def apply(req: HttpRequest) = self(req) map fn
    }
  }

  /**
   * A request reader (implementing reader monad patter) that reads something
   * of type ''A'' for the ''HttpRequest''.
   *
   * @tparam A the result type
   */
  trait RequestReader[A] { self =>

    /**
     * Reads the data from given ''req''.
     *
     * @param req the request to read
     */
    def apply(req: HttpRequest): A

    def flatMap[B](fn: A => RequestReader[B]) = new RequestReader[B] {
      def apply(req: HttpRequest) = fn(self(req))(req)
    }

    def map[B](fn: A => B) = new RequestReader[B] {
      def apply(req: HttpRequest) = fn(self(req))
    }
  }

  /**
   * An empty ''FutureRequestReader''.
   */
  object NoFutureParams extends FutureRequestReader[Nothing] {
    def apply(req: HttpRequest) = new NoSuchElementException("Empty reader.").toFutureException
  }

  /**
   * An empty ''RequestReader''.
   */
  object NoParams extends RequestReader[Nothing] {
    def apply(req: HttpRequest) = throw new NoSuchElementException("Empty reader.")
  }

  private[this] object StringToNumberOrFail {
    def apply[A](rule: String)(number: => A) = new FutureRequestReader[A] {
      def apply(req: HttpRequest) =
        try number.toFuture
        catch { case _: NumberFormatException => new ValidationFailed(rule).toFutureException }
    }
  }

  private[this] object SomeStringToSomeNumber {
    def apply[A](fn: String => A)(o: Option[String]) = o.flatMap { s =>
      try Some(fn(s))
      catch { case _: NumberFormatException => None }
    }
  }

  private[this] object StringsToNumbers {
    def apply[A](fn: String => A)(l: List[String]) = l.flatMap { s =>
      try List(fn(s))
      catch { case _: NumberFormatException => Nil }
    }
  }

  /**
   * A required string param.
   */
  object RequiredParam {

    /**
     * Creates a ''FutureRequestReader'' that reads a required string ''param''
     * from the request or raises an exception when the param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = new FutureRequestReader[String] {
      def apply(req: HttpRequest) = req.params.get(param) match {
        case Some("") => new ValidationFailed(param + " should not be empty").toFutureException
        case Some(value) => value.toFuture
        case None => new ParamNotFound(param).toFutureException
      }
    }
  }

  /**
   * A required integer param.
   */
  object RequiredIntParam {

    /**
     * Creates a ''FutureRequestReader'' that reads a required integer ''param''
     * from the request or raises an exception when the param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToNumberOrFail(param + " should be integer")(s.toInt)
    } yield n
  }

  /**
   * A required long param.
   */
  object RequiredLongParam {

    /**
     * Creates a ''FutureRequestReader'' that reads a required long ''param''
     * from the request or raises an exception when the param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToNumberOrFail(param + " should be long")(s.toLong)
    } yield n
  }

  /**
   * A required boolean param.
   */
  object RequiredBooleanParam {

    /**
     * Creates a ''FutureRequestReader'' that reads a required boolean ''param''
     * from the request or raises an exception when the param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToNumberOrFail(param + " should be boolean")(s.toBoolean)
    } yield n
  }

  /**
   * An optional string param.
   */
  object OptionalParam {

    /**
     * Creates a ''FutureRequestReader'' that reads an optional string ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = new FutureRequestReader[Option[String]] {
      def apply(req: HttpRequest) = req.params.get(param).toFuture
    }
  }

  /**
   * An optional int param.
   */
  object OptionalIntParam {

    /**
     * Creates a ''FutureRequestReader'' that reads an optional integer ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeNumber(_.toInt)(o)
  }

  /**
   * An optional long param.
   */
  object OptionalLongParam {

    /**
     * Creates a ''FutureRequestReader'' that reads an optional long ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeNumber(_.toLong)(o)
  }

  /**
   * An optional boolean param.
   */
  object OptionalBooleanParam {

    /**
     * Creates a ''FutureRequestReader'' that reads an optional boolean ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeNumber(_.toBoolean)(o)
  }

  /**
   * A string param.
   */
  object Param {

    /**
     * Creates a ''RequestReader'' that reads an optional string ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = new RequestReader[Option[String]] {
      def apply(req: HttpRequest) = req.params.get(param)
    }
  }

  /**
   * An integer param.
   */
  object IntParam {

    /**
     * Creates a ''RequestReader'' that reads an optional integer ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- Param(param)
    } yield SomeStringToSomeNumber(_.toInt)(o)
  }

  /**
   * A long param.
   */
  object LongParam {

    /**
     * Creates a ''RequestReader'' that reads an optional long ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- Param(param)
    } yield SomeStringToSomeNumber(_.toLong)(o)
  }

  /**
   * A boolean param.
   */
  object BooleanParam {

    /**
     * Creates a ''RequestReader'' that reads an optional boolean ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- Param(param)
    } yield SomeStringToSomeNumber(_.toBoolean)(o)
  }

  /**
   * A validation rule.
   */
  object ValidationRule {

    /**
     * Creates a ''FutureRequestReader'' that raises a ''ValidationFailed'' exception
     * with message ''rule'' when the given ''predicated'' is evaluated as ''false''.
     *
     * @param rule the exception message
     * @param predicate the predicate to test
     *
     * @return nothing or exception
     */
    def apply(rule: String)(predicate: => Boolean) = new FutureRequestReader[Unit] {
      def apply(req: HttpRequest) =
       if (predicate) Future.Done
       else new ValidationFailed(rule).toFutureException
    }
  }

  object RequiredParams {
    def apply(param: String) = new FutureRequestReader[List[String]] {
      def apply(req: HttpRequest) = req.params.getAll(param).toList.flatMap(_.split(",")) match {
        case Nil => new ParamNotFound(param).toFutureException
        case unfiltered => unfiltered.filter(_ != "") match {
          case Nil => new ValidationFailed(param + " should not be empty").toFutureException
          case filtered => filtered.toFuture
        }
      }
    }
  }

  object RequiredIntParams {
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToNumberOrFail(param + " should be integer")(ss.map { _.toInt })
    } yield ns
  }

  object RequiredLongParams {
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToNumberOrFail(param + " should be integer")(ss.map { _.toLong })
    } yield ns
  }

  object RequiredBooleanParams {
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToNumberOrFail(param + " should be integer")(ss.map { _.toBoolean })
    } yield ns
  }

  object OptionalParams {
    def apply(param: String) = new FutureRequestReader[List[String]] {
      def apply(req: HttpRequest) = req.params.getAll(param).toList.flatMap(_.split(",")).toFuture
    }
  }

  object OptionalIntParams {
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToNumbers(_.toInt)(l)
  }

  object OptionalLongParams {
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToNumbers(_.toLong)(l)
  }

  object OptionalBooleanParams {
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToNumbers(_.toBoolean)(l)
  }

  object Params {
    def apply(param: String) = new RequestReader[List[String]] {
      def apply(req: HttpRequest) = req.params.getAll(param).toList.flatMap(_.split(","))
    }
  }

  object IntParams {
    def apply(param: String) = for {
      l <- Params(param)
    } yield StringsToNumbers(_.toInt)(l)
  }

  object LongParams {
    def apply(param: String) = for {
      l <- Params(param)
    } yield StringsToNumbers(_.toLong)(l)
  }

  object BooleanParams {
    def apply(param: String) = for {
      l <- Params(param)
    } yield StringsToNumbers(_.toBoolean)(l)
  }
}
