package io.finch

import cats.effect.{Effect, ExitCase, IO, SyncIO}
import com.twitter.util.{Return, Throw, Try}

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

  def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] = fa.flatMap(f)

  def tailRecM[A, B](a: A)(f: A => Try[Either[A, B]]): Try[B] = f(a) match {
    case t: Throw[_] => t.asInstanceOf[Try[B]]
    case Return(Left(a1)) => tailRecM(a1)(f)
    case Return(Right(b)) => Return(b)
  }

  def raiseError[A](e: Throwable): Try[A] = Throw[A](e)

  def handleErrorWith[A](fa: Try[A])(f: Throwable => Try[A]): Try[A] = fa.rescue {
    case t => f(t)
  }

  def pure[A](x: A): Try[A] = Try(x)

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

object tried extends Module[Try]()(new TryEffect)
