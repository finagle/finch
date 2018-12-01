package io.finch.instances
import cats.effect.IO
import com.twitter.util.{Future => TwitterFuture, Return, Throw}
import cats.~>

object io {  
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
