package io.finch.generic

import cats.effect.Effect
import io.finch._
import shapeless._

final class GenericDerivation[F[_]: Effect, A] {

  def fromParams[Repr <: HList](
      implicit
      gen: LabelledGeneric.Aux[A, Repr],
      fp: FromParams[F, Repr]
    ): Endpoint[F, A] = fp.endpoint.map(gen.from)
}
