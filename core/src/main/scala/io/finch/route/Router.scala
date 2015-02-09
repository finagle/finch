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
 * A router that extracts some value of the type `A` from the given route.
 */
trait RouterN[+A] { self =>

  /**
   * Extracts some value of type `A` from the given `route`. In case of success it returns `Some` tuple of the _rest_ of
   * the route and the fetched _value_. In case of failure it returns `None`.
   */
  def apply(route: Route): Option[(Route, A)]

  /**
   * Maps this router to the given function `A => B`.
   */
  def map[B](fn: A => B): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = for {
      (r, a) <- self(route)
    } yield (r, fn(a))
    override def toString = self.toString
  }

  /**
   * Flat-maps the router to the given function `A => Option[B]`. If the given function `None` the resulting router will
   * also return `None`.
   */
  def embedFlatMap[B](fn: A => Option[B]): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = for {
      (r, a) <- self(route)
      b <- fn(a)
    } yield (r, b)
    override def toString = self.toString
  }

  /**
   * Flat-maps this router to the given function `A => RouterN[B]`.
   */
  def flatMap[B](fn: A => RouterN[B]): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = for {
      (r, a) <- self(route)
      (rr, b) <- fn(a)(r)
    } yield (rr, b)
    override def toString = self.toString
  }

  /**
   * Flat-maps this router to the given [[io.finch.route.Router0 Router0]].
   */
  def flatMap(rm: => Router0): RouterN[A] = new RouterN[A] {
    def apply(route: Route): Option[(Route, A)] = for {
      (r, a) <- self(route)
      rr <- rm(r)
    } yield (rr, a)

    override def toString = s"${self.toString}/${rm.toString}"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   *
   * Router composition via `orElse` operator happens in a _greedy_ manner: it minimizes the output route tail. Thus, if
   * both of the routers can handle the given `route` the router is being chosen is that which eats more.
   */
  def orElse[B >: A](that: RouterN[B]): RouterN[B] = new RouterN[B] {
    def apply(route: Route): Option[(Route, B)] = (self(route), that(route)) match {
      case (aa @ Some((a, _)), bb @ Some((b, _))) =>
        if (a.length < b.length) aa else bb
      case (a, b) => a orElse b
    }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /[B](that: RouterN[B]): RouterN[A / B] = new RouterN[A / B] {
    val ab = for { a <- self; b <- that } yield new /(a, b)
    def apply(route: Route): Option[(Route, /[A, B])] = ab(route)
    override def toString = s"${self.toString}/${that.toString}"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /(that: Router0): RouterN[A] =
    this flatMap that

  /**
   * Maps this router to the given function `A => B`.
   */
  def />[B](fn: A => B): RouterN[B] =
    this map fn

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed if either this or
   * `that` routers are succeed.
   */
  def |[B >: A](that: RouterN[B]): RouterN[B] =
    this orElse that

  // A workaround for https://issues.scala-lang.org/browse/SI-1336
  def withFilter(p: A => Boolean) = self
}

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
   * Flat-maps this router to the given [[io.finch.route.RouterN RouterN]].
   */
  def flatMap[A](re: => RouterN[A]): RouterN[A] = new RouterN[A] {
    def apply(route: Route): Option[(Route, A)] =
      self(route) flatMap { r => re(r) }
    override def toString = s"${self.toString}/${re.toString}"
  }

  /**
   * Flat-maps this router to the given [[io.finch.route.Router0 Router0]].
   */
  def flatMap(rm: => Router0): Router0 = new Router0 {
    def apply(route: Route): Option[Route] =
      self(route) flatMap { r => rm(r) }
    override def toString = s"${self.toString}/${rm.toString}"
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
        if (a.length < b.length) aa else bb
      case (a, b) => a orElse b
    }

    override def toString = s"(${self.toString}|${that.toString})"
  }

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /(that: Router0): Router0 =
    this flatMap that

  /**
   * Sequentially composes this router with the given `that` router. The resulting router will succeed only if both this
   * and `that` routers are succeed.
   */
  def /[A](that: RouterN[A]): RouterN[A] =
    this flatMap that

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
