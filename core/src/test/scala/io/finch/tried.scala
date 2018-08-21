package io.finch

import cats.effect.{Effect, IO}
import com.twitter.util.{Return, Throw, Try}
import io.catbird.util.twitterTryInstance
import io.finch.endpoint.effect.EffectEndpoints
import io.finch.syntax.EndpointMappers

object tried extends EffectEndpoints[Try] with Outputs with ValidationRules with EffectInstances[Try] {
  implicit def E: Effect[Try] = new Effect[Try] {
    def runAsync[A](fa: Try[A])(cb: Either[Throwable, A] => IO[Unit]): IO[Unit] =
      IO {
        fa match {
          case Return(a) => cb(Right(a))
          case Throw(t) => cb(Left(t))
        }
      }

    def async[A](k: (Either[Throwable, A] => Unit) => Unit): Try[A] = {
      var t: Try[A] = null
      k {
        case Right(r) =>
          t = Return(r)
        case Left(l) =>
          t = Throw(l)
      }
      t
    }

    def suspend[A](thunk: => Try[A]): Try[A] = thunk

    def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] = twitterTryInstance.flatMap(fa)(f)

    def tailRecM[A, B](a: A)(f: A => Try[Either[A, B]]): Try[B] = twitterTryInstance.tailRecM(a)(f)

    def raiseError[A](e: Throwable): Try[A] = twitterTryInstance.raiseError(e)

    def handleErrorWith[A](fa: Try[A])(f: Throwable => Try[A]): Try[A] = twitterTryInstance.handleErrorWith(fa)(f)

    def pure[A](x: A): Try[A] = twitterTryInstance.pure(x)
  }

  object syntax extends EndpointMappers[Try]
}
