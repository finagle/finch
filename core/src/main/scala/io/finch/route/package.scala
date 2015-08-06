package io.finch

import com.twitter.finagle.Service
import shapeless._
import shapeless.ops.adjoin.Adjoin

/**
 * This package contains various of functions and types that enable _router combinators_ in Finch. Finch's
 * [[route.Router]] is an abstraction that is responsible for routing the HTTP requests using their
 * method and path information.
 */
package object route extends RouterCombinators {

  /**
   * An alias for [[io.finch.route.Router Router]] that maps route to a [[com.twitter.finagle.Service Service]].
   */
  @deprecated(message = "Endpoint is deprecated in favor of coproduct routers", since = "0.8.0")
  type Endpoint[A, B] = Router[Service[A, B]]

  type Router0 = Router[HNil]
  type Router2[A, B] = Router[A :: B :: HNil]
  type Router3[A, B, C] = Router[A :: B :: C :: HNil]

  implicit def stringToMatcher(s: String): Router0 = new Matcher(s)
  implicit def intToMatcher(i: Int): Router0 = new Matcher(i.toString)
  implicit def booleanToMatcher(b: Boolean): Router0 = new Matcher(b.toString)
}

package route {
  /**
   * An exception, which is thrown by router in case of missing route `r`.
   */
  case class RouteNotFound(r: String) extends Exception(s"Route not found: $r")

  /**
   * We need a version of [[shapeless.ops.adjoin.Adjoin]] that provides slightly different behavior in
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
