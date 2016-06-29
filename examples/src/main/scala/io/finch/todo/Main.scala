package io.finch.todo

import java.util.UUID

import com.twitter.app.Flag
import com.twitter.finagle.Http
import com.twitter.finagle.stats.Counter
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

/**
 * A simple Finch application implementing the backend for the TodoMVC project.
 *
 * Use the following sbt command to run the application.
 *
 * {{{
 *   $ sbt 'examples/runMain io.finch.todo.Main'
 * }}}
 *
 * Use the following HTTPie commands to test endpoints.
 *
 * {{{
 *   $ http POST :8081/todos title=foo order:=0 completed:=false
 *   $ http PATCH :8081/todos/<UUID> completed:=false
 *   $ http :8081/todos
 *   $ http DELETE :8081/todos
 * }}}
 */
object Main extends TwitterServer {

  val port: Flag[Int] = flag("port", 8081, "TCP port for HTTP server")

  val todos: Counter = statsReceiver.counter("todos")

  val postedTodo: Endpoint[Todo] =
    body.as[UUID => Todo].map(_(UUID.randomUUID()))

  val getTodos: Endpoint[List[Todo]] = get("todos") {
    Ok(Todo.list())
  }

  val postTodo: Endpoint[Todo] = post("todos" :: postedTodo) { t: Todo =>
    todos.incr()
    Todo.save(t)

    Created(t)
  }

  val deleteTodo: Endpoint[Todo] = delete("todos" :: uuid) { id: UUID =>
    Todo.get(id) match {
      case Some(t) => Todo.delete(id); Ok(t)
      case None => throw new TodoNotFound(id)
    }
  }

  val deleteTodos: Endpoint[List[Todo]] = delete("todos") {
    val all: List[Todo] = Todo.list()
    all.foreach(t => Todo.delete(t.id))

    Ok(all)
  }

  val patchedTodo: Endpoint[Todo => Todo] = body.as[Todo => Todo]

  val patchTodo: Endpoint[Todo] =
    patch("todos" :: uuid :: patchedTodo) { (id: UUID, pt: Todo => Todo) =>
      Todo.get(id) match {
        case Some(currentTodo) =>
          val newTodo: Todo = pt(currentTodo)
          Todo.delete(id)
          Todo.save(newTodo)

          Ok(newTodo)
        case None => throw TodoNotFound(id)
      }
    }

  val api = (getTodos :+: postTodo :+: deleteTodo :+: deleteTodos :+: patchTodo).handle {
    case e: TodoNotFound => NotFound(e)
  }

  def main(): Unit = {
    log.info("Serving the Todo application")

    val server = Http.server
      .withStatsReceiver(statsReceiver)
      .serve(s":${port()}", ServiceBuilder()
        .respond[Application.Json](api)
        .respond[Text.Plain](get("ping") { Ok("pong") } )
        .toService
      )

    onExit { server.close() }

    Await.ready(adminHttpServer)
  }
}
