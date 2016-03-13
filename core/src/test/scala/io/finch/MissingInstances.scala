package io.finch

import java.util.UUID

import algebra.Eq
import com.twitter.util.{Return, Throw, Try}

/**
 * Type class instances for non-Finch types.
 */
trait MissingInstances {
  implicit def eqTry[A](implicit A: Eq[A]): Eq[Try[A]] = Eq.instance {
    case (Return(a), Return(b)) => A.eqv(a, b)
    case (Throw(x), Throw(y)) => x == y
    case _ => false
  }
  implicit def eqUUID: Eq[UUID] = Eq.fromUniversalEquals
}
