package io.finch.todo

import cats.effect.{IO, Ref, Resource}
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.http.Status
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._

class App(idRef: Ref[IO, Int], storeRef: Ref[IO, Map[Int, Todo]]) extends Endpoint.Module[IO] {

  final val postedTodo: Endpoint[IO, Todo] =
    jsonBody[(Int, Boolean) => Todo].mapAsync(pt => idRef.modify(id => (id + 1, pt(id, false))))

  final val patchedTodo: Endpoint[IO, Todo => Todo] =
    jsonBody[Todo => Todo]

  final val postTodo: Endpoint[IO, Todo] = post("todos" :: postedTodo) { todo: Todo =>
    storeRef.modify(store => (store + (todo.id -> todo), Created(todo)))
  }

  final val patchTodo: Endpoint[IO, Todo] =
    patch("todos" :: path[Int] :: patchedTodo) { (id: Int, pt: Todo => Todo) =>
      storeRef.modify { store =>
        store.get(id) match {
          case Some(currentTodo) =>
            val newTodo = pt(currentTodo)
            (store + (id -> newTodo), Ok(newTodo))
          case None =>
            (store, Output.empty(Status.NotFound))
        }
      }
    }

  final val getTodos: Endpoint[IO, List[Todo]] = get("todos") {
    storeRef.get.map(todos => Ok(todos.values.toList.sortBy(-_.id)))
  }

  final val deleteTodo: Endpoint[IO, Todo] = delete("todos" :: path[Int]) { id: Int =>
    storeRef.modify { store =>
      store.get(id) match {
        case Some(t) => (store - id, Ok(t))
        case None    => (store, Output.empty(Status.NotFound))
      }
    }
  }

  final val deleteTodos: Endpoint[IO, List[Todo]] = delete("todos") {
    storeRef.modify(store => (Map.empty, Ok(store.values.toList.sortBy(-_.id))))
  }

  final def listen(address: String): Resource[IO, ListeningServer] = Bootstrap[IO]
    .serve[Application.Json](getTodos :+: postTodo :+: deleteTodo :+: deleteTodos :+: patchTodo)
    .serve[Text.Html](classpathAsset("/todo/index.html"))
    .serve[Application.Javascript](classpathAsset("/todo/main.js"))
    .listen(address)
}
