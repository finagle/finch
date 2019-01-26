package io.finch

import cats.effect.{ConcurrentEffect, Effect, IO}
import com.twitter.finagle.{Service => FinagleService}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Promise}

trait Filter[F[_]] extends ((Request, Service[F]) => F[(Trace, Response)]) {
  self =>

  def andThen(s: Service[F]): Service[F] = new Service[F] {
    def apply(req: Request): F[(Trace, Response)] = {
      self(req, s)
    }
  }

  def andThen(other: Filter[F]): Filter[F] = {
    new Filter[F] {
      def apply(req: Request, s: Service[F]): F[(Trace, Response)] = self.andThen(other.andThen(s))(req)
    }
  }

}

abstract class Service[F[_]] extends (Request => F[(Trace, Response)]) {
  self =>

  def toFinagleService(implicit F: Effect[F]): FinagleService[Request, Response] =
    new FinagleService[Request, Response] {
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
