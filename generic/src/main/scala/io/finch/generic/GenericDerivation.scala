package io.finch.generic

import cats.Monad
import io.finch._
import shapeless._

final class GenericDerivation[F[_]: Monad, A] {
  def fromParams[Repr <: HList](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      fp: FromParams[F, Repr]
  ): Endpoint[F, A] = fp.endpoint.map(gen.from)
}
