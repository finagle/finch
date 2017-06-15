package io.finch.internal

import cats.MonadError
import com.twitter.util.{Future, Try}
import scala.annotation.tailrec
import scala.util.{Either, Left, Right}

/**
 * Makes Twitter [[Future]] re-runnable.
 *
 * This was originally designed and implemented by Travis Brown (https://twitter.com/travisbrown)
 * for the Catbird project. Finch then forked it to avoid extra dependency and simplify the release
 * process.
 *
 * @see https://github.com/finagle/finch/issues/793
 * @see https://github.com/travisbrown/catbird
 */
abstract class Rerunnable[A] { self =>
  def run: Future[A]

  final def map[B](f: A => B): Rerunnable[B] = new Rerunnable[B] {
    final def run: Future[B] = self.run.map(f)
  }

  final def flatMap[B](f: A => Rerunnable[B]): Rerunnable[B] = new Rerunnable.Bind[A, B](this, f)

  final def flatMapF[B](f: A => Future[B]): Rerunnable[B] = new Rerunnable[B] {
    final def run: Future[B] = self.run.flatMap(f)
  }

  final def product[B](other: Rerunnable[B]): Rerunnable[(A, B)] = new Rerunnable[(A, B)] {
    final def run: Future[(A, B)] = self.run.join(other.run)
  }

  final def liftToTry: Rerunnable[Try[A]] = new Rerunnable[Try[A]] {
    final def run: Future[Try[A]] = self.run.liftToTry
  }

  @tailrec
  final def step: Rerunnable[A] = this match {
    case outer: Rerunnable.Bind[_, A] => outer.fa match {
      case inner: Rerunnable.Bind[_, _] => inner.fa.flatMap(x => inner.ff(x).flatMap(outer.ff)).step
      case _ => this
    }
    case _ => this
  }
}

object Rerunnable {
  private class Bind[A, B](
      val fa: Rerunnable[A],
      val ff: A => Rerunnable[B]) extends Rerunnable[B] with (A => Future[B]) {

    final def apply(a: A): Future[B] = ff(a).run

    final def run: Future[B] = step match {
      case bind: Bind[A, B] => bind.fa.run.flatMap(bind)
      case other => other.run
    }
  }

  def const[A](a: A): Rerunnable[A] = new Rerunnable[A] {
    final def run: Future[A] = Future.value(a)
  }

  def apply[A](a: => A): Rerunnable[A] = new Rerunnable[A] {
    final def run: Future[A] = Future(a)
  }

  def fromFuture[A](fa: => Future[A]): Rerunnable[A] = new Rerunnable[A] {
    final def run: Future[A] = fa
  }

  implicit val rerunnableInstance: MonadError[Rerunnable, Throwable] =
    new MonadError[Rerunnable, Throwable] {
      final def pure[A](a: A): Rerunnable[A] = Rerunnable.const(a)

      override final def map[A, B](fa: Rerunnable[A])(f: A => B): Rerunnable[B] = fa.map(f)

      override final def product[A, B](fa: Rerunnable[A], fb: Rerunnable[B]): Rerunnable[(A, B)] =
        fa.product(fb)

      final def flatMap[A, B](fa: Rerunnable[A])(f: A => Rerunnable[B]): Rerunnable[B] =
        fa.flatMap(f)

      final def raiseError[A](e: Throwable): Rerunnable[A] = new Rerunnable[A] {
        final def run: Future[A] = Future.exception[A](e)
      }

      final def handleErrorWith[A](fa: Rerunnable[A])(
        f: Throwable => Rerunnable[A]
      ): Rerunnable[A] = new Rerunnable[A] {
        final def run: Future[A] = fa.run.rescue {
          case error => f(error).run
        }
      }

      final def tailRecM[A, B](a: A)(f: A => Rerunnable[Either[A, B]]): Rerunnable[B] = f(a).flatMap {
        case Right(b) => pure(b)
        case Left(nextA) => tailRecM(nextA)(f)
      }
    }
}
