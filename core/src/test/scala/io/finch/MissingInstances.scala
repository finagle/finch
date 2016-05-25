package io.finch

import java.util.UUID

import cats.Eq
import cats.Show
import com.twitter.io.Buf
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

  implicit def showUUID: Show[UUID] = Show.fromToString

  implicit def eqBuf: Eq[Buf] = Eq.fromUniversalEquals
}
