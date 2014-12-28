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

import com.twitter.finagle.Service
import io.finch.request.DecodeRequest
import io.finch.response._

package object json {

  /**
   * An abstraction that is responsible for JSON to string encoding.
   */
  trait EncodeJson[-A] extends EncodeResponse[A] {
    def contentType = "application/json"
  }

  /**
   * An abstraction that is responsible for string to JSON decoding.
   */
  trait DecodeJson[+A] extends DecodeRequest[A] {
    def apply(json: String): Option[A]
  }

  /**
   * A service that converts JSON into HTTP response with status ''OK''.
   */
  class TurnJsonIntoHttp[A](val encode: EncodeJson[A]) extends Service[A, HttpResponse] {
    def apply(req: A) = Ok(req)(encode).toFuture
  }

  object TurnJsonIntoHttp {
    def apply[A](implicit encode: EncodeJson[A]) = new TurnJsonIntoHttp[A](encode)
  }
}
