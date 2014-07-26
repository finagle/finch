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

import io.finch.json._
import io.finch.response._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Base64StringEncoder
import org.jboss.netty.handler.codec.http.HttpHeaders

package object filter {

  /**
   * A facet that converts JSON into HTTP response with status ''OK''.
   *
   * @param formatter a json formatter
   * @tparam Req the request type
   */
  class TurnJsonIntoHttp[Req](formatter: JsonFormatter = DefaultJsonFormatter)
      extends Facet[Req, JsonResponse, HttpResponse] {

    def apply(req: Req)(rep: JsonResponse) = Ok(rep, formatter).toFuture
  }

  /**
   * A companion object for ''TurnJsonIntoHttp'' facet.
   */
  object TurnJsonIntoHttp {
    def apply[Req] = new TurnJsonIntoHttp[Req]()
    def apply[Req](formatter: JsonFormatter = DefaultJsonFormatter) = new TurnJsonIntoHttp[Req](formatter)
  }

  case class BasicallyAuthorize(user: String, password: String) extends SimpleFilter[HttpRequest, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {
      val userInfo = s"$user:$password"
      val expected = "Basic " + Base64StringEncoder.encode(userInfo.getBytes)

      req.headerMap.get(HttpHeaders.Names.AUTHORIZATION) match {
        case Some(actual) if actual == expected => service(req)
        case _ => Unauthorized().toFuture
      }
    }
  }
}
