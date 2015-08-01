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

package io.finch.response

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Response, Request}
import com.twitter.finagle.httpx.path.Path
import io.finch._

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
  def apply(url: String): Service[Request, Response] = new Service[Request, Response] {
    override def apply(req: Request) = SeeOther.withHeaders(("Location", url))().toFuture
  }

  /**
   * Create a Service to generate redirects to the given Path.
   *
   * @param path The Finagle Path to redirect to
   *
   * @return A Service that generates a redirect to the given path
   */
  def apply(path: Path): Service[Request, Response] = this(path.toString)
}
