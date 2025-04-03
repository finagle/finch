package io.finch.todo

import cats.effect._
import cats.effect.unsafe.implicits.global
import com.twitter.server.TwitterServer
import io.finch._

import java.util.concurrent.CountDownLatch

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
object Main extends TwitterServer with EndpointModule[IO] {
  private val port = flag("port", 8081, "TCP port for HTTP server")

  val app: IO[App] = for {
    id <- Ref[IO].of(0)
    store <- Ref[IO].of(Map.empty[Int, Todo])
  } yield new App(id, store)

  val run: IO[Unit] =
    Resource.eval(app).flatMap(_.listen(s":${port()}")).useForever

  locally {
    println(s"Open your browser at http://localhost:${port()}/todo/index.html") // scalastyle:ignore
    val latch = new CountDownLatch(1)
    val handle: PartialFunction[Throwable, IO[Unit]] = { case e => IO(exitOnError(e)) }
    val cancel = run.onError(handle).unsafeRunCancelable()

    onExit {
      cancel()
      latch.countDown()
    }

    latch.await()
  }
}
