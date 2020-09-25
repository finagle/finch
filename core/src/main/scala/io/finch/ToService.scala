package io.finch

import cats.effect.{ConcurrentEffect, Effect, IO}
import cats.syntax.flatMap._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Promise}

/**
  * Representation of `Endpoint.Compiled` as Finagle Service
  */
case class ToService[F[_]](compiled: Endpoint.Compiled[F])(implicit F: Effect[F]) extends Service[Request, Response] {
  def apply(request: Request): Future[Response] = {
    val repF = compiled(request).flatMap { case (trc, either) =>
      Trace.captureIfNeeded(trc)
      F.fromEither(either)
    }
    val rep = new Promise[Response]
    val run = (F match {
      case concurrent: ConcurrentEffect[F] =>
        (concurrent.runCancelable(repF) _).andThen(io =>
          io.map(cancelToken =>
            rep.setInterruptHandler { case _ =>
              concurrent.toIO(cancelToken).unsafeRunAsyncAndForget()
            }
          )
        )
      case e => e.runAsync(repF) _
    }) {
      case Left(t)  => IO(rep.setException(t))
      case Right(v) => IO(rep.setValue(v))
    }

    run.unsafeRunSync()
    rep
  }
}
