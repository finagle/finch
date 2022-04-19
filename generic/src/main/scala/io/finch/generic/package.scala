package io.finch

import cats.Monad

package object generic {

  /** Generically derive a very basic instance of [[Endpoint]] for a given type `A`.
    */
  def deriveEndpoint[F[_]: Monad, A]: GenericDerivation[F, A] = new GenericDerivation[F, A]
}
