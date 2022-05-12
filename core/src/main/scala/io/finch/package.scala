package io

import cats.effect.IO

/** This is a root package of the Finch library, which provides an immutable layer of functions and types atop of Finagle for writing lightweight HTTP services.
  */
package object finch extends Outputs {

  object catsEffect extends EndpointModule[IO]

}
