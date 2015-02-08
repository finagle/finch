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
 * Contributor(s):
 * Ryan Plessner
 * Pedro Viegas
 */

package io.finch.response

import com.twitter.finagle.httpx.{Response, Cookie, Status}

/**
 * A companion factory object for ''HttpResponse''.
 *
 * @param status the http response status
 * @param headers the HTTP headers map
 */
case class ResponseBuilder(
  status: Status,
  headers: Map[String, String] = Map.empty,
  cookies: Seq[Cookie] = Seq.empty
) {

  /**
   * Creates a new respond with given ''headers''.
   *
   * @param headers the HTTP headers map
   *
   * @return a respond with headers
   */
  def withHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

  /**
   * Create a new ResponseBuilder with the given ''cookies''.
   * @param cookies The ''Cookie''s to add to the response
   * @return a ResponseBuilder with the cookies
   */
  def withCookies(cookies: Cookie*) = copy(cookies = this.cookies ++ cookies)

  /**
   * Creates a ''text/plain'' http response.
   *
   * @param plain the response body
   *
   * @return a plain text http response
   */
  def apply(plain: String) = {
    val rep = Response(status)
    rep.setContentString(plain)
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }

  /**
   * Creates an http response with content-type according to the implicit encode.
   *
   * @param body the response body
   * @return an http response
   */
  def apply[A](body: A)(implicit encode: EncodeResponse[A]) = {
    val rep = Response(status)
    rep.setContentType(encode.contentType)
    rep.setContentString(encode(body))
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }

  /**
   * Creates an empty http response.
   *
   * @return an empty http response
   */
  def apply() = {
    val rep = Response(status)
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }
}
