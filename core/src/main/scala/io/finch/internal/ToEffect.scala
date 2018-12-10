package io.finch.internal

import cats.Eval
import cats.arrow.FunctionK
import cats.effect.Effect
import cats.syntax.flatMap._
import com.twitter.util.{Future => TwitterFuture, Return, Throw}
import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}

trait ToEffect[A[_], B[_]] extends FunctionK[A, B]

object ToEffect {

  def evalEffect[F[_], E[_]: Effect](implicit
    toEffect: ToEffect[F, E]
  ): ToEffect[Lambda[A => Eval[F[A]]], E] = new ToEffect[Lambda[A => Eval[F[A]]], E] {
    def apply[A](fa: Eval[F[A]]): E[A] = Effect[E].delay(fa.value).flatMap { f =>
      toEffect(f)
    }
  }

  implicit def idEffect[E[_]: Effect]: ToEffect[E, E] = new ToEffect[E, E] {
    def apply[A](a: E[A]): E[A] = a
  }

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
