package io.finch.generic

import io.finch._
import shapeless._

final class GenericDerivation[A] {
  def fromParams[Repr <: HList](implicit
    gen: LabelledGeneric.Aux[A, Repr],
    fp: FromParams[Repr]
  ): Endpoint[A] = fp.endpoint.map(gen.from)
}
