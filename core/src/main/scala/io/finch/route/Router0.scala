/*
 * Copyright 2015, by Vladimir Kostyukov and Contributors.
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

package io.finch.route

import shapeless._

/**
 * A router that match the given route to some predicate.
 */
trait Router0 extends RouterN[HNil] { self =>
  /**
   * Matches the given `route` to some predicate and returns `Some` of the _rest_ of the route in case of success or
   * `None` otherwise.
   */
  def matchRoute(route: Route): Option[Route]

  /**
   * A default implementation based on `matchRoute`.
   */
  def apply(route: Route): Option[(Route, HNil)] = matchRoute(route).map(r => (r, HNil))
}

/**
 * A [[io.finch.route.Router0 Router0]] that skips one route token.
 */
object * extends Router0 {
  def matchRoute(route: Route): Option[Route] = Some(route.drop(1))
  override def toString = "*"
}

/**
 * A [[io.finch.route.Router0 Router0]] that skips all route tokens.
 */
object ** extends Router0 {
  def matchRoute(route: Route): Option[Route] = Some(Nil)
  override def toString = "**"
}
