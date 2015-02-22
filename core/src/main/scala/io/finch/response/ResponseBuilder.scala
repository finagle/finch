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

import io.finch._
import com.twitter.finagle.httpx.{Response, Cookie, Status}

/**
 * An abstraction that is responsible for building HTTP responses.
 *
 * @param status the HTTP response status
 * @param headers the HTTP headers map
 * @param cookies the HTTP cookies list
 */
case class ResponseBuilder(
  status: Status,
  headers: Map[String, String] = Map.empty[String, String],
  cookies: Seq[Cookie] = Seq.empty[Cookie]
) {

  /**
   * Creates a new response builder with the given `headers`.
   *
   * @param headers the HTTP headers map
   */
  def withHeaders(headers: (String, String)*): ResponseBuilder = copy(headers = this.headers ++ headers)

  /**
   * Create a new response builder with the given ''cookies''.
   *
   * @param cookies the [[com.twitter.finagle.httpx.Cookie Cookie]]'s to add to the response
   */
  def withCookies(cookies: Cookie*): ResponseBuilder= copy(cookies = this.cookies ++ cookies)

  /**
   * Builds an HTTP response of the given `body` with content-type according to the implicit
   * [[io.finch.response.EncodeResponse EncodeResponse]].
   *
   * @param body the response body
   */
  def apply[A](body: A)(implicit encode: EncodeResponse[A]): HttpResponse = {
    val rep = Response(status)
    rep.setContentType(encode.contentType)
    rep.setContentString(encode(body))
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }

  /**
   * Builds an empty HTTP response.
   */
  def apply(): HttpResponse = {
    val rep = Response(status)
    headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    cookies.foreach { rep.addCookie }

    rep
  }
}
