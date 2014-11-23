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
 */

package io.finch

import io.finch.json.EncodeJson
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import com.twitter.finagle.http.{Status, Response}
import com.twitter.finagle.http.path.Path
import com.twitter.finagle.Service

package object response {

  /**
   * A companion factory object for ''HttpResponse''.
   *
   * @param status the http response status
   * @param headers the HTTP headers map
   */
  case class Respond(status: HttpResponseStatus, headers: Map[String, String] = Map.empty) {

    /**
     * Creates a new respond with given ''headers''.
     *
     * @param headers the HTTP headers map
     *
     * @return a respond with headers
     */
    def withHeaders(headers: (String, String)*) = copy(headers = this.headers ++ headers)

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
      headers.foreach { case (k, v) => rep.headers().add(k, v) }

      rep
    }

    /**
     * Creates an ''application/json'' http response.
     *
     * @param json the response body
     *
     * @return a json http response
     */
    def apply[A](json: A)(implicit encode: EncodeJson[A]) = {
      val rep = Response(status)
      rep.setContentTypeJson()
      rep.setContentString(encode(json))
      headers.foreach { case (k, v) => rep.headers().add(k, v) }

      rep
    }

    /**
     * Creates an empty http response.
     *
     * @return an empty http response
     */
    def apply() = {
      val rep = Response(status)
      headers.foreach { case (k, v) => rep.headers().add(k, v) }

      rep
    }
  }

  //
  // The most popular HTTP Responses
  //
  object Ok extends Respond(Status.Ok)                                   // 200
  object Created extends Respond(Status.Created)                         // 201
  object Accepted extends Respond(Status.Accepted)                       // 202
  object NoContent extends Respond(Status.NoContent)                     // 204
  object MovedPermanently extends Respond(Status.MovedPermanently)       // 301
  object SeeOther extends Respond(Status.SeeOther)                       // 303
  object NotModified extends Respond(Status.NotModified)                 // 304
  object TemporaryRedirect extends Respond(Status.TemporaryRedirect)     // 307
  object BadRequest extends Respond(Status.BadRequest)                   // 400
  object Unauthorized extends Respond(Status.Unauthorized)               // 401
  object PaymentRequired extends Respond(Status.PaymentRequired)         // 402
  object Forbidden extends Respond(Status.Forbidden)                     // 403
  object NotFound extends Respond(Status.NotFound)                       // 404
  object MethodNotAllowed extends Respond(Status.MethodNotAllowed)       // 405
  object NotAcceptable extends Respond(Status.NotAcceptable)             // 406
  object RequestTimeOut extends Respond(Status.RequestTimeout)           // 408
  object Conflict extends Respond(Status.Conflict)                       // 409
  object PreconditionFailed extends Respond(Status.PreconditionFailed)   // 412
  object TooManyRequests extends Respond(Status.TooManyRequests)         // 429
  object InternalServerError extends Respond(Status.InternalServerError) // 500
  object NotImplemented extends Respond(Status.NotImplemented)           // 501
  object BadGateway extends Respond(Status.BadGateway)                   // 502
  object ServiceUnavailable extends Respond(Status.ServiceUnavailable)   // 503

  /**
   * A factory for Redirecting to other URLs.
   */
  object Redirect {

    /**
     * Create a Service to generate redirects to the given url.
     *
     * @param url The url to redirect to
     *
     * @return A Service that generates a redirect to the given url
     */
    def apply(url: String): Service[HttpRequest, HttpResponse] = new Service[HttpRequest, HttpResponse] {
      def apply(req: HttpRequest) = SeeOther.withHeaders(("Location", url))().toFuture
    }

    /**
     * Create a Service to generate redirects to the given Path.
     *
     * @param path The Finagle Path to redirect to
     *
     * @return A Service that generates a redirect to the given path
     */
    def apply(path: Path): Service[HttpRequest, HttpResponse] = this(path.toString)
  }
}
