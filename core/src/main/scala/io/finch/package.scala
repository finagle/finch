package io

import cats.effect.IO

/** This is a root package of the Finch library, which provides an immutable layer of functions and types atop of Finagle for writing lightweight HTTP services.
  */
package object finch extends Outputs with ValidationRules {

  type ToAsync[F[_], E[_]] = internal.ToAsync[F, E]

  object catsEffect extends EndpointModule[IO]

}
