package io.finch

import cats.effect.{ConcurrentEffect, Effect, IO}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Promise}

/**
  * A compiled endpoint that is represented as a function of `Request => F[(Trace, Response)]`
  * with ability to convert it to `com.twitter.finagle.Service`
  */
abstract class EndpointCompiled[F[_]] extends (Request => F[(Trace, Response)]) { self =>

  def toService(implicit F: Effect[F]): Service[Request, Response] =
    new Service[Request, Response] {
      def apply(request: Request): Future[Response] = {
        val repF = self(request)
        val rep = new Promise[Response]
        val run = (F match {
          case concurrent: ConcurrentEffect[F] =>
            (concurrent.runCancelable(repF) _).andThen(io => io.map(cancelToken =>
              rep.setInterruptHandler {
                case _ => concurrent.toIO(cancelToken).unsafeRunAsyncAndForget()
              }
            ))
          case e => e.runAsync(repF) _
        }) {
          case Left(t) => IO(rep.setException(t))
          case Right((_, v)) => IO(rep.setValue(v))
        }

        run.unsafeRunSync()
        rep
      }
    }

}
