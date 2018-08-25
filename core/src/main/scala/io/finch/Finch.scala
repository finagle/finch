package io.finch

import cats.effect.Effect
import io.finch.endpoint.effect.EffectEndpoints
import io.finch.syntax.effect.EffectEndpointMappers

abstract class Finch[F[_]](implicit val E: Effect[F])extends EffectEndpoints[F]
  with Outputs
  with ValidationRules
  with EffectInstances[F] {

  type Endpoint[A] = io.finch.Endpoint[F, A]

  object syntax extends EffectEndpointMappers[F]

}
