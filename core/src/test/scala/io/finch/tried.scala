package io.finch

import cats.effect.{Effect, ExitCase, IO, SyncIO}
import com.twitter.util.{Return, Throw, Try}
import io.catbird.util.twitterTryInstance

class TryEffect extends Effect[Try] {
  def runAsync[A](fa: Try[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
    SyncIO {
      fa match {
        case Return(a) => cb(Right(a)).unsafeRunSync()
        case Throw(t) => cb(Left(t)).unsafeRunSync()
      }
    }

  def async[A](k: (Either[Throwable, A] => Unit) => Unit): Try[A] =
    asyncF(k.andThen(u => Try(u)))

  def suspend[A](thunk: => Try[A]): Try[A] = thunk

  def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] = twitterTryInstance.flatMap(fa)(f)

  def tailRecM[A, B](a: A)(f: A => Try[Either[A, B]]): Try[B] = twitterTryInstance.tailRecM(a)(f)

  def raiseError[A](e: Throwable): Try[A] = twitterTryInstance.raiseError(e)

  def handleErrorWith[A](fa: Try[A])(f: Throwable => Try[A]): Try[A] = twitterTryInstance.handleErrorWith(fa)(f)

  def pure[A](x: A): Try[A] = twitterTryInstance.pure(x)

  def asyncF[A](k: (Either[Throwable, A] => Unit) => Try[Unit]): Try[A] = {
    var t: Try[A] = null
    k {
      case Right(r) =>
        t = Return(r)
      case Left(l) =>
        t = Throw(l)
    }
    t
  }

  def bracketCase[A, B](acquire: Try[A])(use: A => Try[B])(release: (A, ExitCase[Throwable]) => Try[Unit]): Try[B] = {
    acquire.flatMap(use)
  }
}

object tried extends Finch[Try]()(new TryEffect)
