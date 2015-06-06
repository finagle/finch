package io.finch.benchmarks.service

import com.twitter.util.Await
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

class ArgonautUserServiceAppTest extends UserServiceAppTest(() => argonaut.userService)
class FinagleUserServiceAppTest extends UserServiceAppTest(() => finagle.userService)
class JacksonUserServiceAppTest extends UserServiceAppTest(() => jackson.userService)
class Json4sUserServiceAppTest extends UserServiceAppTest(() => json4s.userService)

class UserServiceAppTest(
  service: () => UserService
) extends FlatSpec with Matchers with BeforeAndAfter {
  val app = new UserServiceApp(service)(8123, 5, 1)

  before {
    app.setUpService()
  }

  after {
    app.tearDownService()
  }

  "The benchmark application" should "perform create operations correctly" in {
    val result = Await.result(app.runCreateUsers)

    result.size shouldBe app.count

    result.zipWithIndex.foreach {
      case (response, i) =>
        response.statusCode shouldBe 201
        response.location shouldBe Some(s"/users/${ i + app.count }")
    }
  }

  it should "perform get operations correctly" in {
    val result = Await.result(app.runGetUsers)

    result.size shouldBe (app.count * 2)

    import io.finch.benchmarks.service.argonaut._
    import _root_.argonaut.Parse

    result.take(app.count).zipWithIndex.foreach {
      case (response, i) =>
        response.statusCode shouldBe 200
        Parse.decodeOption[User](response.contentString) shouldBe
          Some(User(i.toLong, s"name-${ "a" * i }", i, Nil))
    }

    result.drop(app.count).foreach { response =>
      response.statusCode shouldBe 400
    }
  }

  it should "perform update operations correctly" in {
    val result = Await.result(app.runUpdateUsers)

    result.size shouldBe (app.count * 2)

    result.take(app.count).foreach { response =>
      response.statusCode shouldBe 204
    }

    result.drop(app.count).foreach { response =>
      response.statusCode shouldBe 500
    }

    val userResult = Await.result(app.runGetAllUsers)

    import io.finch.benchmarks.service.argonaut._
    import _root_.argonaut.Parse

    val users = Parse.decodeOption[List[User]](userResult.contentString).get

    users.size shouldBe app.count

    users.zipWithIndex.foreach {
      case (user, i) =>
        user shouldBe User(
          i.toLong,
          s"name-${ "b" * i }",
           i,
           List(Status("Foo"), Status("Bar"), Status("Baz"))
        )
    }
  }

  it should "perform get all operations correctly" in {
    val result = Await.result(app.runGetAllUsers)

    result.statusCode shouldBe 200

    import io.finch.benchmarks.service.argonaut._
    import _root_.argonaut.Parse

    val users = Parse.decodeOption[List[User]](result.contentString)

    users.map(_.size) shouldBe Some(app.count)

    users.foreach(
      _.zipWithIndex.foreach {
        case (user, i) =>
          user shouldBe User(i.toLong, s"name-${ "a" * i }", i, Nil)
      }
    )
  }

  it should "perform delete all operations correctly" in {
    val result = Await.result(app.runDeleteAllUsers)

    result.contentString.split(" ").head.toInt shouldBe app.count
  }
}
