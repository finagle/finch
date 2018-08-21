package io.finch

import cats.effect.Effect

trait EffectInstances[F[_]] {

  implicit def E: Effect[F]

}
