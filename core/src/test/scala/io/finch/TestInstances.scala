package io.finch

import cats.Eq
import cats.syntax.all._
import com.twitter.io.Buf

/** Type class instances for non-Finch types. */
trait TestInstances {
  implicit def eqEither[A: Eq]: Eq[Either[Throwable, A]] = {
    case (Right(a), Right(b)) => a eqv b
    case (Left(x), Left(y))   => x == y
    case _                    => false
  }

  implicit def eqBuf: Eq[Buf] =
    Eq.fromUniversalEquals
}
