package io.finch.internal

import cats.effect.Effect
import com.twitter.util.{Future => TwitterFuture}
import scala.concurrent.{Future => ScalaFuture}

trait LiftToEffect[F[_], E[_]] {

  def toEffect[A](f: => F[A]): E[A]

}

object LiftToEffect {

  implicit def twitterFutureLift[E[_] : Effect]: LiftToEffect[TwitterFuture, E] = {
    new LiftToEffect[TwitterFuture, E] {
      def toEffect[A](f: => TwitterFuture[A]): E[A] = Effect[E].async[A] { cb =>
        f
          .onFailure(t => cb(Left(t)))
          .onSuccess(b => cb(Right(b)))
      }
    }
  }

  implicit def scalaFutureLift[E[_] : Effect]: LiftToEffect[ScalaFuture, E] = {
    new LiftToEffect[ScalaFuture, E] {
      def toEffect[A](f: => ScalaFuture[A]): E[A] = Effect[E].async[A] { cb =>
        f.onComplete {
          case scala.util.Success(v) => cb(Right(v))
          case scala.util.Failure(t) => cb(Left(t))
        }(DummyExecutionContext)
      }
    }
  }

}