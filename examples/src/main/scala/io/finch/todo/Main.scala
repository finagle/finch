package io.finch.todo

import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import com.twitter.app.Flag
import com.twitter.finagle.Http
import com.twitter.server.TwitterServer
import com.twitter.util.Await

import scala.concurrent.ExecutionContext

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

  def main(): Unit = {
    println(s"Open your browser at http://localhost:${port()}/todo/index.html") // scalastyle:ignore

    val server = for {
      id <- Ref[IO].of(0)
      store <- Ref[IO].of(Map.empty[Int, Todo])
      server <- Dispatcher[IO].use { implicit dispatcher =>
        val app = new App(id, store, ExecutionContext.global)
        val srv = Http.server.withStatsReceiver(statsReceiver)
        IO(srv.serve(s":${port()}", app.toService))
      }
    } yield server
    val handle = server.unsafeRunSync()(IORuntime.global)
    onExit(handle.close())
    Await.ready(adminHttpServer)
  }
}
