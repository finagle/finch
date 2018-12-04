package io.finch.internal

import cats.effect.Effect
import io.finch.ToEffect

trait IdEffect {
  implicit def idEffect[E[_]: Effect]: ToEffect[E, E] = new ToEffect[E, E] {
    def apply[A](a: E[A]): E[A] = a
  }
}
