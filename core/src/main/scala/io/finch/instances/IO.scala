package io.finch
package instances

import cats.effect.IO
import cats.~>
import com.twitter.util.{Future => TwitterFuture, Return, Throw}
import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}

object twitterFuture {
  implicit val twFutureToEffect: TwitterFuture ~> IO = new (TwitterFuture ~> IO) {
    def apply[A](a: TwitterFuture[A]): IO[A] =
      IO.async { cb =>
        a.respond {
          case Return(r) => cb(Right(r))
          case Throw(t) => cb(Left(t))
        }
      }
  }
}

object scalaFuture {
  implicit val scFutureToEffect: ScalaFuture ~> IO = new (ScalaFuture ~> IO) {
    import internal.DummyExecutionContext
    def apply[A](a: ScalaFuture[A]): IO[A] =
      IO.async { cb =>
        a.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(t) => cb(Left(t))
        }(DummyExecutionContext)
      }
  }
}
