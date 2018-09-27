package io.finch.todo

import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers

class TodoSpec extends FlatSpec with Matchers with Checkers {
  import Main._

  behavior of "the postTodo endpoint"

  case class TodoWithoutId(title: String, completed: Boolean, order: Int)

  def genTodoWithoutId: Gen[TodoWithoutId] = for {
    t <- Gen.alphaStr
    c <- Gen.oneOf(true, false)
    o <- Gen.choose(Int.MinValue, Int.MaxValue)
  } yield TodoWithoutId(t, c, o)

  implicit def arbitraryTodoWithoutId: Arbitrary[TodoWithoutId] = Arbitrary(genTodoWithoutId)

  it should "create a todo" in {
    check { todoWithoutId: TodoWithoutId =>
      val input = Input.post("/todos")
        .withBody[Application.Json](todoWithoutId, Some(StandardCharsets.UTF_8))

      val res = postTodo(input)
      val Some(todo) = res.awaitOutputUnsafe()

      todo.status === Status.Created &&
      todo.value.completed === todoWithoutId.completed &&
      todo.value.title === todoWithoutId.title &&
      todo.value.order === todoWithoutId.order &&
      Todo.get(todo.value.id).isDefined
    }
  }

  behavior of "the patchTodo endpoint"

  it should "modify an existing todo if its id has been found" in {
    val todo = createTodo()
    val input = Input.patch(s"/todos/${todo.id}")
      .withBody[Application.Json](Buf.Utf8("{\"completed\": true}"), Some(StandardCharsets.UTF_8))

    patchTodo(input).awaitValueUnsafe() shouldBe Some(todo.copy(completed = true))
    Todo.get(todo.id) shouldBe Some(todo.copy(completed = true))
  }
  it should "throw an exception if the uuid hasn't been found" in {
    val id = UUID.randomUUID()
    Todo.get(id) shouldBe None

    val input = Input.patch(s"/todos/$id")
      .withBody[Application.Json](Buf.Utf8("{\"completed\": true}"), Some(StandardCharsets.UTF_8))

    a[TodoNotFound] shouldBe thrownBy(patchTodo(input).awaitValueUnsafe())
  }

  it should "give back the same todo with non-related json" in {
    val todo = createTodo()
    val input = Input.patch(s"/todos/${todo.id}")
      .withBody[Application.Json](Buf.Utf8("{\"bla\": true}"), Some(StandardCharsets.UTF_8))

    patchTodo(input).awaitValueUnsafe() shouldBe Some(todo)
    Todo.get(todo.id) shouldBe Some(todo)
  }

  behavior of "the getTodos endpoint"

  it should "retrieve all available todos" in {
    getTodos(Input.get("/todos")).awaitValueUnsafe() shouldBe Some(Todo.list())
  }

  behavior of "the deleteTodo endpoint"

  it should "delete the specified todo" in {
    val todo = createTodo()

    deleteTodo(Input.delete(s"/todos/${todo.id}")).awaitValueUnsafe() shouldBe Some(todo)
    Todo.get(todo.id) shouldBe None
  }

  it should "throw an exception if the uuid hasn't been found" in {
    val id = UUID.randomUUID()
    Todo.get(id) shouldBe None
    a[TodoNotFound] shouldBe thrownBy(deleteTodo(Input.delete(s"/todos/$id")).awaitValueUnsafe())
  }

  behavior of "the deleteTodos endpoint"

  it should "delete all todos" in {
    val todos = Todo.list()
    deleteTodos(Input.delete("/todos")).awaitValueUnsafe() shouldBe Some(todos)
    todos.foreach(t => Todo.get(t.id) shouldBe None)
  }

  private def createTodo(): Todo = {
    val todo = Todo(UUID.randomUUID(), "foo", completed = false, 0)
    Todo.save(todo)
    todo
  }
}
