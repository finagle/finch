package io.finch.todo

import cats.effect._
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import com.twitter.app.Flag
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import io.finch.internal.ToAsync

/** A simple Finch server serving a TODO application.
  *
  * Use the following sbt command to run the application.
  *
  * {{{
  *   $ sbt 'examples/runMain io.finch.todo.Main'
  * }}}
  *
  * Open your browser at `http://localhost:8081/todo/index.html` or use the following HTTPie commands to test endpoints.
  *
  * {{{
  *   $ http POST :8081/todos title=foo
  *   $ http PATCH :8081/todos/<ID> completed:=true
  *   $ http :8081/todos
  *   $ http DELETE :8081/todos/<ID>
  *   $ http DELETE :8081/todos
  * }}}
  */
object Main extends TwitterServer {

  private val port: Flag[Int] = flag("port", 8081, "TCP port for HTTP server")

  private def serve(service: Service[Request, Response]): Resource[IO, ListeningServer] =
    Resource.make(for {
      srv <- IO(Http.server.withStatsReceiver(statsReceiver))
      server <- IO(srv.serve(s":${port()}", service))
    } yield server) { server =>
      IO.defer(implicitly[ToAsync[Future, IO]].apply(server.close()))
    }

  val run: IO[Unit] =
    (for {
      _ <- Resource.eval(IO.println(s"Open your browser at http://localhost:${port()}/todo/index.html")) // scalastyle:ignore
      id <- Resource.eval(Ref[IO].of(0))
      store <- Resource.eval(Ref[IO].of(Map.empty[Int, Todo]))
      app = new App(id, store)
      service <- app.toService
      server <- serve(service)
    } yield server).use(_ => IO(Await.ready(adminHttpServer)) >> IO.never)

  def main(): Unit = {
    val dispatcher = new Dispatcher[IO] {
      override def unsafeToFutureCancelable[A](fa: IO[A]): (concurrent.Future[A], () => concurrent.Future[Unit]) = fa.unsafeToFutureCancelable()
    }
    dispatcher.unsafeRunSync(run)
  }
}
