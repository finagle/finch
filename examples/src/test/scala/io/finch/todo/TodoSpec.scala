package io.finch.todo

import cats.effect.IO
import cats.effect.concurrent.Ref
import com.twitter.finagle.http.Status
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import io.finch.internal.DummyExecutionContext
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class TodoSpec extends AnyFlatSpec with Matchers with Checkers {

  behavior of "Todo App"

  case class TodoCompleted(completed: Boolean)

  case class TodoTitle(title: String) {
    def withId(id: Int): Todo = Todo(id, title, completed = false)
  }

  case class AppState(id: Int, store: Map[Int, Todo])

  case class TestApp(
      id: Ref[IO, Int],
      store: Ref[IO, Map[Int, Todo]]
  ) extends App(id, store)(IO.contextShift(DummyExecutionContext)) {
    def state: IO[AppState] = for {
      i <- id.get
      s <- store.get
    } yield AppState(i, s)
  }

  def genTodoWithoutId: Gen[TodoTitle] =
    Gen.alphaStr.map(s => TodoTitle(s))

  def genTestApp: Gen[TestApp] =
    Gen.listOf(genTodoWithoutId).map { todos =>
      val id = todos.length
      val store = todos.zipWithIndex.map { case (t, i) => i -> t.withId(i) }

      TestApp(Ref.unsafe[IO, Int](id), Ref.unsafe[IO, Map[Int, Todo]](store.toMap))
    }

  implicit def arbitraryTodoWithoutId: Arbitrary[TodoTitle] = Arbitrary(genTodoWithoutId)
  implicit def arbitraryApp: Arbitrary[TestApp] = Arbitrary(genTestApp)
  implicit def arbitraryTodoCompleted: Arbitrary[TodoCompleted] =
    Arbitrary(Arbitrary.arbBool.arbitrary.map(TodoCompleted))

  it should "post a todo" in {
    check { (app: TestApp, todo: TodoTitle) =>
      val input = Input.post("/todos").withBody[Application.Json](todo)

      val shouldBeTrue = for {
        prev <- app.state
        posted <- app.postTodo(input).output.get
        next <- app.state
      } yield prev.id + 1 == next.id &&
        prev.store + (prev.id -> posted.value) == next.store &&
        posted.value == todo.withId(prev.id)

      shouldBeTrue.unsafeRunSync()
    }
  }

  it should "patch a todo" in {
    check { (app: TestApp, todo: TodoCompleted) =>
      def input(id: Int): Input = Input.patch(s"/todos/$id").withBody[Application.Json](todo)

      val shouldBeTrue = for {
        prev <- app.state
        patched <- app.patchTodo(input(prev.id - 1)).output.get
        next <- app.state
      } yield (prev.id == 0 && patched.status == Status.NotFound && prev == next) ||
        (
          next.id == prev.id && patched.value.id == prev.id - 1 &&
            patched.value.completed == todo.completed &&
            prev.store + ((prev.id - 1) -> patched.value) == next.store
        )

      shouldBeTrue.unsafeRunSync()
    }
  }
}
