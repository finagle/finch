package io.finch.todo

import java.util.UUID

import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import io.circe.generic.auto._
import io.circe.syntax._
import io.finch.Input
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
    check { (todoWithoutId: TodoWithoutId) =>
      val input = Input.post("/todos")
        .withBody(Buf.Utf8(todoWithoutId.asJson.toString), Some("application/json;charset=utf8"))
      val res = postTodo(input)
      res.output.map(_.status) === Some(Status.Created)
      res.value.isDefined === true
      val Some(todo) = res.value
      todo.completed === todoWithoutId.completed
      todo.title === todoWithoutId.title
      todo.order === todoWithoutId.order
      Todo.get(todo.id).isDefined === true
    }
  }

  behavior of "the patchTodo endpoint"
  it should "modify an existing todo if its id has been found" in {
    val todo = createTodo()
    val input = Input.patch(s"/todos/${todo.id}")
      .withBody(Buf.Utf8("{\"completed\": true}"), Some("application/json;charset=utf8"))
    patchTodo(input).value shouldBe Some(todo.copy(completed = true))
    Todo.get(todo.id) shouldBe Some(todo.copy(completed = true))
  }
  it should "throw an exception if the uuid hasn't been found" in {
    val id = UUID.randomUUID()
    Todo.get(id) shouldBe None
    val input = Input.patch(s"/todos/$id")
      .withBody(Buf.Utf8("{\"completed\": true}"), Some("application/json;charset=utf8"))
    a[TodoNotFound] shouldBe thrownBy(patchTodo(input).value)
  }
  it should "give back the same todo with non-related json" in {
    val todo = createTodo()
    val input = Input.patch(s"/todos/${todo.id}")
      .withBody(Buf.Utf8("{\"bla\": true}"), Some("application/json;charset=utf8"))
    patchTodo(input).value shouldBe Some(todo)
    Todo.get(todo.id) shouldBe Some(todo)
  }

  behavior of "the getTodos endpoint"
  it should "retrieve all available todos" in {
    getTodos(Input.get("/todos")).value shouldBe Some(Todo.list())
  }

  behavior of "the deleteTodo endpoint"
  it should "delete the specified todo" in {
    val todo = createTodo()
    deleteTodo(Input.delete(s"/todos/${todo.id}")).value shouldBe Some(todo)
    Todo.get(todo.id) shouldBe None
  }
  it should "throw an exception if the uuid hasn't been found" in {
    val id = UUID.randomUUID()
    Todo.get(id) shouldBe None
    a[TodoNotFound] shouldBe thrownBy(deleteTodo(Input.delete(s"/todos/$id")).value)
  }

  behavior of "the deleteTodos endpoint"
  it should "delete all todos" in {
    val todos = Todo.list()
    deleteTodos(Input.delete("/todos")).value shouldBe Some(todos)
    todos.foreach(t => Todo.get(t.id) shouldBe None)
  }

  private def createTodo(): Todo = {
    val todo = Todo(UUID.randomUUID(), "foo", completed = false, 0)
    Todo.save(todo)
    todo
  }
}
