package io.finch

import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import cats.effect.{IO, SyncIO}

import scala.concurrent.Future

object Dispatchers {
  lazy val forIO: Dispatcher[IO] = new Dispatcher[IO] {
    implicit val runtime: IORuntime = IORuntime.global
    def unsafeToFutureCancelable[A](fa: IO[A]) = fa.unsafeToFutureCancelable()
  }

  val forSyncIO: Dispatcher[SyncIO] = new Dispatcher[SyncIO] {
    def unsafeToFutureCancelable[A](fa: SyncIO[A]) =
      (Future.fromTry(fa.attempt.unsafeRunSync().toTry), () => Future.unit)
  }
}
