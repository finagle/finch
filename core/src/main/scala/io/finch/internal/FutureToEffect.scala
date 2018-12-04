package io.finch.internal

import cats.effect.Effect
import com.twitter.util.{Future => TwitterFuture, Return, Throw}
import io.finch.ToEffect
import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}

trait FutureToEffect {
  implicit def twFutureToEffect[E[_]: Effect]: ToEffect[TwitterFuture, E] = new ToEffect[TwitterFuture, E] {
    def apply[A](a: TwitterFuture[A]): E[A] =
      Effect[E].async { cb =>
        a.respond {
          case Return(r) => cb(Right(r))
          case Throw(t) => cb(Left(t))
        }
      }
  }
  implicit def scFutureToIO[E[_]: Effect]: ToEffect[ScalaFuture, E] = new ToEffect[ScalaFuture, E] {
    def apply[A](a: ScalaFuture[A]): E[A] =
      Effect[E].async { cb =>
        a.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(t) => cb(Left(t))
        }(DummyExecutionContext)
      }
  }
}
