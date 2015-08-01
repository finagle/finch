/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.request

import com.twitter.finagle.httpx.Request

/**
 * A type class that provides a conversion from some type to a [[com.twitter.finagle.httpx.Request]].
 */
trait ToRequest[R] {
  def apply(r: R): Request
}

object ToRequest {
  /**
   * A convenience method that supports creating a [[ToRequest]] instance from
   * a function.
   */
  def apply[R](converter: R => Request): ToRequest[R] = new ToRequest[R] {
    def apply(r: R): Request = converter(r)
  }

  /**
   * An identity instance for [[com.twitter.finagle.httpx.Request]] itself.
   */
  implicit val requestIdentity: ToRequest[Request] = apply(identity)
}
