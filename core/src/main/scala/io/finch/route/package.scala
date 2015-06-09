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

package io.finch

import com.twitter.finagle.Service
import shapeless._
import shapeless.ops.adjoin.Adjoin

/**
 * This package contains various of functions and types that enable _router combinators_ in Finch. A Finch
 * [[io.finch.route.Router Router]] is an abstraction that is responsible for routing the HTTP requests using their
 * method and path information. There are two types of routers in Finch: [[io.finch.route.Matcher Matcher]] and
 * [[io.finch.route.Router Router]]. `Matcher` matches the route and returns an `Option` of the rest of the route.
 * `Router[A]` in addition to the `Matcher` behaviour extracts a value of type `A` from the route.
 *
 * A [[io.finch.route.Router Router]] that maps route to a [[com.twitter.finagle.Service Service]] is called an
 * [[io.finch.route.Endpoint Endpoint]]. An endpoint `Req => Rep` might be implicitly converted into a
 * `Service[Req, Rep]`. Thus, the following example is a valid Finch code:
 *
 * {{{
 *   def hello(s: String) = new Service[HttRequest, HttpResponse] {
 *     def apply(req: HttpRequest) = Ok(s"Hello $name!").toFuture
 *   }
 *
 *   Httpx.serve(
 *     new InetSocketAddress(8081),
 *     Get / string /> hello // will be implicitly converted into service
 *   )
 * }}}
 */
package object route extends RouterCombinators {

  /**
   * An alias for [[io.finch.route.Router Router]] that maps route to a [[com.twitter.finagle.Service Service]].
   */
  type Endpoint[A, B] = Router[Service[A, B]]

  type Router0 = Router[HNil]
  type Router2[A, B] = Router[A :: B :: HNil]
  type Router3[A, B, C] = Router[A :: B :: C :: HNil]

  implicit def stringToMatcher(s: String): Router0 = Matcher(s)
  implicit def intToMatcher(i: Int): Router0 = Matcher(i.toString)
  implicit def booleanToMatcher(b: Boolean): Router0 = Matcher(b.toString)
}

package route {
  /**
   * An exception, which is thrown by router in case of missing route `r`.
   */
  case class RouteNotFound(r: String) extends Exception(s"Route not found: $r")

  /**
   * We need a version of [[shapeless.adjoin.Adjoin]] that provides slightly different behavior in
   * the case of singleton results (we simply return the value, not a singleton `HList`).
   */
  trait PairAdjoin[A, B] extends DepFn2[A, B]

  trait LowPriorityPairAdjoin {
    type Aux[A, B, Out0] = PairAdjoin[A, B] { type Out = Out0 }

    implicit def pairAdjoin[A, B, Out0](implicit
      adjoin: Adjoin.Aux[A :: B :: HNil, Out0]
    ): Aux[A, B, Out0] =
      new PairAdjoin[A, B] {
        type Out = Out0

        def apply(a: A, b: B): Out0 = adjoin(a :: b :: HNil)
      }
  }

  object PairAdjoin extends LowPriorityPairAdjoin {
    implicit def singletonPairAdjoin[A, B, C](implicit
      adjoin: Adjoin.Aux[A :: B :: HNil, C :: HNil]
    ): Aux[A, B, C] = new PairAdjoin[A, B] {
      type Out = C

      def apply(a: A, b: B): C = adjoin(a :: b :: HNil).head
    }
  }
}
