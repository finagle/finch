package io.finch

import scala.concurrent.ExecutionContext

import cats.Eq
import cats.effect.{ContextShift, IO}
import com.twitter.io.Buf

/**
  * Type class instances for non-Finch types.
  */
trait MissingInstances {
  implicit def eqEither[A](implicit A: Eq[A]): Eq[Either[Throwable, A]] = Eq.instance {
    case (Right(a), Right(b)) => A.eqv(a, b)
    case (Left(x), Left(y))   => x == y
    case _                    => false
  }

  implicit def eqBuf: Eq[Buf] = Eq.fromUniversalEquals

  implicit val shift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
}
