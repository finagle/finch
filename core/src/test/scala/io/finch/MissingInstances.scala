package io.finch

import cats.Eq
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import com.twitter.io.Buf

import scala.concurrent.Future

/** Type class instances for non-Finch types.
  */
trait MissingInstances {

  implicit val dispatcherIO: Dispatcher[IO] = new Dispatcher[IO] {
    override def unsafeToFutureCancelable[A](fa: IO[A]): (Future[A], () => Future[Unit]) =
      fa.unsafeToFutureCancelable()
  }

  implicit def eqEither[A](implicit A: Eq[A]): Eq[Either[Throwable, A]] = Eq.instance {
    case (Right(a), Right(b)) => A.eqv(a, b)
    case (Left(x), Left(y))   => x == y
    case _                    => false
  }

  implicit def eqBuf: Eq[Buf] = Eq.fromUniversalEquals
}
