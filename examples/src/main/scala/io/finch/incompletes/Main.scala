package io.finch.incompletes.todo

import java.util.UUID
import com.twitter.app.Flag
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.param.Stats
import com.twitter.finagle.stats.Counter
import com.twitter.server.TwitterServer
import com.twitter.util.{Future, Await}
import io.finch.{Endpoint, EncodeResponse}
import io.finch.RequestReader._
import io.finch._

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
 *   $ http POST :8081/todos title==foo order==0 completed==false n==23 seq==553 seq==666
 *   $ http PATCH :8081/todos/<UUID> completed==false
 *   $ http :8081/todos
 *   $ http DELETE :8081/todos
 * }}}
 */


object Main extends TwitterServer {

  implicit val encodeTodo: EncodeResponse[Todo] =
    EncodeResponse.fromString("text/plain")(x=> x.toString)

  implicit val encodeTodoList: EncodeResponse[List[Todo]] =
    EncodeResponse.fromString("text/plain")(x=> x.mkString("\n"))

  implicit val encodeException: EncodeResponse[Exception] =
    EncodeResponse.fromString("text/plain")( x=> x.toString)

  implicit val encodeTrow: EncodeResponse[Throwable] =
    EncodeResponse.fromString("text/plain")( x=> x.toString)


  val port: Flag[Int] = flag("port", 8081, "TCP port for HTTP server")

  val todos: Counter = statsReceiver.counter("todos")

  val postedTodo: RequestReader[Todo] = derive[UUID => Todo].incomplete.map(_(UUID.randomUUID()))

  //we may recieve the remaining parameter from rpc
  val postedTodoFuture: RequestReader[Future[Todo]] = derive[UUID => Todo].incomplete.map(x =>
    Future.value(UUID.randomUUID()) map(z=> x(z)))

  val adultTodoFuture: RequestReader[Future[Todo]] = derive[UUID => Todo].incomplete.map(x=>
    Future.value(UUID.randomUUID()) map(z=> x(z))).withFilterF(z=> z map(q => q.order > 18))

  val completeTodo: RequestReader[Todo] = derive[Todo].fromParams

  val getTodos: Endpoint[List[Todo]] = get("todos") {
    Ok(Todo.list())
  }

  val postTodo: Endpoint[Todo] = post("todos" ? postedTodo) { t: Todo =>
    todos.incr()
    Todo.save(t)
    Created(t)
  }

  val postTodoFuture: Endpoint[Todo] = post("todos" / "future" ? postedTodoFuture) { futuret: Future[Todo]=>
    futuret map { t =>
      todos.incr()
      Todo.save(t)
      Created(t)
    }
  }

  val postTodoComlpete: Endpoint[Todo] =
    post("todos" / "complete" ? completeTodo) { t: Todo =>
      todos.incr()
      Todo.save(t)
      Created(t)
    }

  val postTodoAdultFuture: Endpoint[Todo] =
    post("todos" / "future" / "adult" ? adultTodoFuture) { futuret: Future[Todo] =>
    futuret map { t =>
      todos.incr()
      Todo.save(t)
      Created(t)
    }
  }


  val deleteTodo: Endpoint[Todo] = delete("todos" / uuid) { id: UUID =>
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

/*
 implicit val patchedTodo: RequestReader[Todo => Todo] =
    RequestReader.derive[Todo].patch

  val patchTodo: Endpoint[Todo] =
    patch("todos" / uuid ? patchedTodo) { (id: UUID, pt: Todo => Todo) =>
      Todo.get(id) match {
        case Some(currentTodo) =>
          val newTodo: Todo = pt(currentTodo)
          Todo.delete(id)
          Todo.save(newTodo)

          Ok(newTodo)
        case None => throw TodoNotFound(id)
      }
    }
 */

  val api: Service[Request, Response] = (
    getTodos :+: postTodo :+: deleteTodo :+: deleteTodos :+:
      postTodoFuture :+: postTodoAdultFuture :+: postTodoComlpete
    ).handle({
    case e: TodoNotFound => NotFound(e)
  }).toService

  def main(): Unit = {
    log.info("Serving the Todo Incompletes application (extract from request params)")

    val server = Http.server
      .configured(Stats(statsReceiver))
      .serve(s":${port()}", api)

    onExit { server.close() }

    Await.ready(adminHttpServer)
  }
}
