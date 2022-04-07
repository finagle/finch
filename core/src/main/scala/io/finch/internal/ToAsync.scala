package io.finch.internal

import cats.effect.Async
import cats.~>
import com.twitter.util.{Future => TwitterFuture, Return, Throw}

import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}

trait ToAsync[A[_], B[_]] extends ~>[A, B]

object ToAsync {

  implicit def idAsync[E[_]: Async]: ToAsync[E, E] = new ToAsync[E, E] {
    def apply[A](a: E[A]): E[A] = a
  }

  implicit def twFutureToAsync[E[_]: Async]: ToAsync[TwitterFuture, E] = new ToAsync[TwitterFuture, E] {
    def apply[A](a: TwitterFuture[A]): E[A] =
      Async[E].async_ { cb =>
        a.respond {
          case Return(r) => cb(Right(r))
          case Throw(t)  => cb(Left(t))
        }
      }
  }

  implicit def scFutureToAsync[E[_]: Async]: ToAsync[ScalaFuture, E] = new ToAsync[ScalaFuture, E] {
    def apply[A](a: ScalaFuture[A]): E[A] =
      Async[E].async_ { cb =>
        a.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(t) => cb(Left(t))
        }(DummyExecutionContext)
      }
  }
}
