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

/**
 * A router that match the given route to some predicate.
 */
trait Router0 { self =>

  /**
   * Matches the given `route` to some predicate and returns `Some` of the _rest_ of the route in case of success or
   * `None` otherwise.
   */
  def apply(route: Route): Option[Route]

  /**
   * Maps this router to the given value `a`.
   */
  def map[A](a: => A): RouterN[A] = new RouterN[A] {
    def apply(route: Route): Option[(Route, A)] = for {
      r <- self(route)
    } yield (r, a)
    override def toString = self.toString
  }

  /**
   * Sequentially composes this router with the given `that` [[io.finch.route.RouterN RouterN]].
   */
  def andThen[A](that: => RouterN[A]): RouterN[A] = new RouterN[A] {
    def apply(route: Route): Option[(Route, A)] =
      self(route) flatMap { r => that(r) }
    override def toString = s"${self.toString}/${that.toString}"
  }

  /**
   * Sequentially composes this router with the given `that` [[io.finch.route.Router0 Router0]].
   */
  def andThen(that: => Router0): Router0 = new Router0 {
    def apply(route: Route): Option[Route] =
      self(route) flatMap { r => that(r) }
    override def toString = s"${self.toString}/${that.toString}"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   *
   * Router composition via `orElse` operator happens in a _greedy_ manner: it minimizes the output route tail. Thus,
   * if both of the routers can handle the given `route` the router is being chosen is that which eats more.
   */
  def orElse(that: Router0): Router0 = new Router0 {
    def apply(route: Route): Option[Route] = (self(route), that(route)) match {
      case (aa @ Some(a), bb @ Some(b)) =>
        if (a.length <= b.length) aa else bb
      case (a, b) => a orElse b
    }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /(that: Router0): Router0 =
    this andThen that

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /[A](that: RouterN[A]): RouterN[A] =
    this andThen that

  /**
   * Maps this router to some value.
   */
  def />[A](a: => A): RouterN[A] =
    this map a

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   */
  def |(that: Router0): Router0 =
    this orElse that
}

/**
 * A [[io.finch.route.Router0 Router0]] that skips one route token.
 */
object * extends Router0 {
  def apply(route: Route): Option[Route] = Some(route.drop(1))
  override def toString = "*"
}

/**
 * A [[io.finch.route.Router0 Router0]] that skips all route tokens.
 */
object ** extends Router0 {
  def apply(route: Route): Option[Route] = Some(Nil)
  override def toString = "**"
}
