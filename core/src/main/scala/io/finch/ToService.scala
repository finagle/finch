package io.finch

import cats.effect.Async
import cats.effect.std.Dispatcher
import cats.implicits._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Promise}

/** Representation of `Endpoint.Compiled` as Finagle Service
  */
case class ToService[F[_]](compiled: Endpoint.Compiled[F])(implicit F: Async[F], dispatcher: Dispatcher[F]) extends Service[Request, Response] {
  def apply(request: Request): Future[Response] = {
    val repF = compiled(request).flatMap { case (trc, either) =>
      Trace.captureIfNeeded(trc)
      F.fromEither(either)
    }
    val rep = new Promise[Response]
    val cancel = dispatcher.unsafeRunCancelable(repF.attempt.map(_.fold(rep.setException, rep.setValue)))
    rep.setInterruptHandler { case _ => cancel() }
    rep
  }
}
