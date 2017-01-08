package io.finch

package object generic {
  /**
   * Generically derive a very basic instance of [[Endpoint]] for a given type `A`.
   */
  def deriveEndpoint[A]: GenericDerivation[A] = new GenericDerivation[A]
}
