package io

import cats.effect.{ConcurrentEffect, Effect, IO}
import cats.syntax.all._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Promise}

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and
 * types atop of Finagle for writing lightweight HTTP services.
  */
package object finch extends Outputs with ValidationRules {

  type ToEffect[F[_], E[_]] = internal.ToEffect[F, E]

  object catsEffect extends EndpointModule[IO]

  object items {
    sealed abstract class RequestItem(val kind: String, val nameOption:Option[String] = None) {
      val description = kind + nameOption.fold("")(" '" + _ + "'")
    }
    final case class ParamItem(name: String) extends RequestItem("param", Some(name))
    final case class HeaderItem(name: String) extends RequestItem("header", Some(name))
    final case class CookieItem(name: String) extends RequestItem("cookie", Some(name))
    case object BodyItem extends RequestItem("body")
    case object MultipleItems extends RequestItem("request")
  }

  implicit class CompiledOps[F[_]](compiled: Endpoint.Compiled[F]) {

    /**
      * Convert `Endpoint.Compiled[F]` to Finagle Service
      */
    def toService(implicit F: Effect[F]): Service[Request, Response] =
      new Service[Request, Response] {
        def apply(request: Request): Future[Response] = {
          val repF = compiled(request).flatMap {
            case (trc, either) =>
              Trace.captureIfNeeded(trc)
              F.fromEither(either)
          }
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
            case Right(v) => IO(rep.setValue(v))
          }

          run.unsafeRunSync()
          rep
        }
      }

  }

}
