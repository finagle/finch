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

import org.jboss.netty.handler.codec.http.HttpResponseStatus
import com.twitter.finagle.http.{Status, Response}

/**
 * A companion factory object for ''HttpResponse''.
 *
 * @param status the http response status
 */
case class Reply(status: HttpResponseStatus) {

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

    rep
  }

  /**
   * Creates an ''application/json'' http response.
   *
   * @param json the response body
   * @param formatter the json formatter
   *
   * @return a json http response
   */
  def apply(json: JsonResponse, formatter: JsonFormatter = DefaultJsonFormatter) = {
    val rep = Response(status)
    rep.setContentTypeJson()
    rep.setContentString(json.toString(formatter))

    rep
  }

  /**
   * Creates an empty http response.
   *
   * @return an empty http response
   */
  def apply() = Response(status)
}

//
// Top-10 HTTP response statuses
//
object Ok extends Reply(Status.Ok)
object Created extends Reply(Status.Created)
object NoContent extends Reply(Status.NoContent)
object NotModified extends Reply(Status.NotModified)
object BadRequest extends Reply(Status.BadRequest)
object Unauthorized extends Reply(Status.Unauthorized)
object Forbidden extends Reply(Status.Forbidden)
object NotFound extends Reply(Status.NotFound)
object Conflict extends Reply(Status.Conflict)
object InternalServerError extends Reply(Status.InternalServerError)

