/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
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

import io.finch.json._
import com.twitter.finagle.http._
import com.twitter.util.Future
import com.twitter.finagle.{Filter, Service}
import scala.util.parsing.json.JSONType
import scala.util.parsing.json.JSONArray
import scala.util.parsing.json.JSONObject

/***
 * Hi! I'm Finch.io - a super-tiny library atop of Finagle that makes the
 * development of RESTFul API services more pleasant and slick.
 *
 * I'm trying to follow the principles of my elder brother and keep the things
 * as composable as possible.
 *
 *   (a) In order to mark the difference between filters and facets and show the
 *       direction of a data-flow, the facets are composed by ''!'' operator
 *       within a reversed order:
 *
 *        '''val s = service ! facetA ! facetB'''
 *
 *       Note: facets don't change the request type.
 *
 *   (b) Endpoints might be treated as partial functions over the routes, so they
 *       may be composed together with ''orElse'' operator:
 *
 *        '''val r = endpointA orElse endpointB'''
 *
 *   (d) Endpoints may also be composed with filters by using the ''!'' operator
 *       in a familiar way:
 *
 *        '''val r = authorize ! endpoint'''
 *
 *       Note: ''authorize'' changes the request type.
 *
 *   (e) Finagle filters may also be composed with ''!'' operator:
 *
 *        '''val f = filterA ! filterB ! filterC'''
 *
 * Have fun writing a reusable and scalable code with me!
 *
 * - https://github.com/finagle/finch
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
     * Composes this filter within given endpoint ''next'' endpoint.
     *
     * @param next an endpoint to compose
     *
     * @return an endpoint composed with filter
     */
    def !(next: Endpoint[ReqOut, RepIn]) =
      next andThen { service =>
        filter andThen service
      }

    /**
     * Composes this filter within given ''next'' filter.
     *
     * @param next the next filter in the chain
     * @tparam Req the request type
     * @tparam Rep the response type
     *
     * @return a filter composed within next filter
     */
    def ![Req, Rep](next: Filter[ReqOut, RepIn, Req, Rep]) = filter andThen next

    /**
     * Composes this filter within given ''next'' service.
     *
     * @param next the service to compose
     *
     * @return a service composed with filter
     */
    def !(next: Service[ReqOut, RepIn]) = filter andThen next
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
     * Composes this service with a given ''next'' filter.
     *
     * @param next a filter to compose
     * @tparam RepOut an output response type
     *
     * @return a new service composed with facet.
     */
    def ![RepOut](next: Filter[Req, RepOut, Req, RepIn]) = next andThen service

    /**
     * Composes this service with given ''next'' service.
     *
     * @param next a service to compose
     * @tparam RepOut an output response type
     *
     * @return a new service compose with other service.
     */
    def ![RepOut](next: Service[RepIn, RepOut]) =
      new Service[Req, RepOut] {
        def apply(req: Req) = service(req) flatMap next
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
}
