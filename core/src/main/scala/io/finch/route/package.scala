package io.finch

import shapeless._

/**
 * This package contains various of functions and types that enable _router combinators_ in Finch. Finch's
 * [[io.finch.Endpoint]] is an abstraction that is responsible for routing the HTTP requests using their
 * method and path information.
 *
 * Please note that this package is deprecated since 0.8.5.
 */
package object route extends Endpoints {

  @deprecated("Use io.finch.Endpoint instead", "0.8.5")
  type Router[A] = Endpoint[A]
  @deprecated("Use io.finch.Endpoint0 instead", "0.8.5")
  type Router0 = Endpoint[HNil]
  @deprecated("Use io.finch.Endpoint2 instead", "0.8.5")
  type Router2[A, B] = Endpoint[A :: B :: HNil]
  @deprecated("Use io.finch.Endpoint3 instead", "0.8.5")
  type Router3[A, B, C] = Endpoint[A :: B :: C :: HNil]

  @deprecated("Use io.finch.Endpoint instead", "0.8.5")
  val Router = Endpoint
}
