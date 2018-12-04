package io.finch.instances

import cats.effect.IO
import com.twitter.util.{Future => TwitterFuture, Return, Throw}
import io.finch.ToEffect
import io.finch.internal.DummyExecutionContext
import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}

trait ToIO {
  implicit val twFutureToIO: ToEffect[TwitterFuture, IO] = new ToEffect[TwitterFuture, IO] {
    def apply[A](a: TwitterFuture[A]): IO[A] =
      IO.async { cb =>
        a.respond {
          case Return(r) => cb(Right(r))
          case Throw(t) => cb(Left(t))
        }
      }
  }
  implicit val scFutureToIO: ToEffect[ScalaFuture, IO] = new ToEffect[ScalaFuture, IO] {
    def apply[A](a: ScalaFuture[A]): IO[A] =
      IO.async { cb =>
        a.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(t) => cb(Left(t))
        }(DummyExecutionContext)
      }
  }
}
