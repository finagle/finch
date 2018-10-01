package io.finch.todo

import java.util.UUID

import cats.effect.IO
import com.twitter.app.Flag
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
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
 *   $ http PATCH :8081/todos/<UUID> completed:=true
 *   $ http :8081/todos
 *   $ http DELETE :8081/todos/<UUID>
 *   $ http DELETE :8081/todos
 * }}}
 */
object Main extends TwitterServer with Endpoint.Module[IO] {

  val port: Flag[Int] = flag("port", 8081, "TCP port for HTTP server")

  val todos: Counter = statsReceiver.counter("todos")

  def postedTodo: Endpoint[IO, Todo] = jsonBody[UUID => Todo].map(_(UUID.randomUUID()))

  def postTodo: Endpoint[IO, Todo] = post("todos" :: postedTodo) { t: Todo =>
    todos.incr()
    Todo.save(t)

    Created(t)
  }

  def patchedTodo: Endpoint[IO, Todo => Todo] = jsonBody[Todo => Todo]

  def patchTodo: Endpoint[IO, Todo] =
    patch("todos" :: path[UUID] :: patchedTodo) { (id: UUID, pt: Todo => Todo) =>
      Todo.get(id) match {
        case Some(currentTodo) =>
          val newTodo: Todo = pt(currentTodo)
          Todo.delete(id)
          Todo.save(newTodo)

          Ok(newTodo)
        case None => throw TodoNotFound(id)
      }
    }

  def getTodos: Endpoint[IO, List[Todo]] = get("todos") {
    Ok(Todo.list())
  }

  def deleteTodo: Endpoint[IO, Todo] = delete("todos" :: path[UUID]) { id: UUID =>
    Todo.get(id) match {
      case Some(t) => Todo.delete(id); Ok(t)
      case None => throw TodoNotFound(id)
    }
  }

  def deleteTodos: Endpoint[IO, List[Todo]] = delete("todos") {
    val all: List[Todo] = Todo.list()
    all.foreach(t => Todo.delete(t.id))

    Ok(all)
  }

  val api: Service[Request, Response] = (
    getTodos :+: postTodo :+: deleteTodo :+: deleteTodos :+: patchTodo
  ).handle({
    case e: TodoNotFound => NotFound(e)
  }).toServiceAs[Application.Json]

  def main(): Unit = {
    println("Serving the Todo application") //scalastyle:ignore

    val server = Http.server
      .withStatsReceiver(statsReceiver)
      .serve(s":${port()}", api)

    onExit { server.close() }

    Await.ready(adminHttpServer)
  }
}
