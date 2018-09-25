package io.finch

import cats.effect.Effect
import io.finch.syntax.EndpointMappers

abstract class Module[F[_]](implicit val E: Effect[F]) extends Endpoints[F]
  with Outputs
  with ValidationRules
  with EffectInstances[F] {

  type Endpoint[A] = io.finch.Endpoint[F, A]

  object syntax extends EndpointMappers[F]

}
