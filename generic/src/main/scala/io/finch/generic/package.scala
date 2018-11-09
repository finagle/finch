package io.finch

import cats.effect.Effect

package object generic {

  /**
   * Generically derive a very basic instance of [[Endpoint]] for a given type `A`.
   */
  def deriveEndpoint[F[_]: Effect, A]: GenericDerivation[F, A] = new GenericDerivation[F, A]
}
