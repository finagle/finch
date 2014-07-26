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

package io.finch

import com.twitter.util.Future
import com.twitter.finagle.{Service, Filter}

/**
 * A ''Facet'' is just a special kind of filter that doesn't change the request type.
 *
 * @tparam Req the request type
 * @tparam RepIn the input response type
 * @tparam RepOut the output response type
 */
abstract class Facet2[Req, -RepIn, +RepOut] extends Filter[Req, RepOut, Req, RepIn] {

  /**
   * Converts given ''rep'' from ''RepIn'' to ''RepOut'' type.
   *
   * @param rep the response to convert
   *
   * @return a converted response
   */
  def apply(req: Req)(rep: RepIn): Future[RepOut]

  def apply(req: Req, service: Service[Req, RepIn]) = service(req) flatMap apply(req)
}

/**
 * A facet that converts JSON into HTTP response with status ''OK''.
 *
 * @param formatter a json formatter
 * @tparam Req the request type
 */
class TurnJsonIntoHttp[Req](formatter: JsonFormatter = DefaultJsonFormatter)
    extends Facet2[Req, JsonResponse, HttpResponse] {

  def apply(req: Req)(rep: JsonResponse) = Ok(rep, formatter).toFuture
}

/**
 * A companion object for ''TurnJsonIntoHttp'' facet.
 */
object TurnJsonIntoHttp {
  def apply[Req] = new TurnJsonIntoHttp[Req]()
  def apply[Req](formatter: JsonFormatter = DefaultJsonFormatter) = new TurnJsonIntoHttp[Req](formatter)
}