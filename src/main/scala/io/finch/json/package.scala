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

import io.finch.response._
import com.twitter.finagle.Service

package object json {

  trait Json {

    /**
     *
     * @return A ''Json'' object must return valid json from its ''toString'' method.
     */
    def toString: String
  }

  /**
   * A service that converts JSON into HTTP response with status ''OK''.
   *
   */
  object TurnJsonIntoHttp extends Service[JsonResponse, HttpResponse] {

    def apply(req: JsonResponse) = Ok(req).toFuture
  }
}
