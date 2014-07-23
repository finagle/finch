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

