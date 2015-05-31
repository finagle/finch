package io.finch.playground

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.Httpx
import com.twitter.util.Await

import io.finch.{Endpoint => _, _}
import io.finch.request._
import io.finch.route._
import io.finch.jackson._

/**
 * GET /user/groups        -> Seq[Group]
 * POST /groups?name=foo   -> Group
 * PUT /user/groups/:group -> User
 */
object Main extends App {

  // enable finch-jackson magic
  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  // model
  case class Group(ownerId: Int = 0, name: String)
  case class User(id: Int, groups: Seq[Group])

  case class UnknownUser(id: Int) extends Exception(s"Unknown user with id=$id")
  case object WrongSecret extends Exception("You shell not pass!")

  val currentUser: RequestReader[User] =
    header("X-Secret").embedFlatMap {
      case "open sesame" => User(0, Seq.empty[Group]).toFuture
      case _ => WrongSecret.toFutureException
    }

  // GET /user/groups -> Seq[Group]
  val getUserGroups: Router[RequestReader[Seq[Group]]] =
    Get / "user" / "groups" /> (currentUser ~> { user => Seq(Group(user.id, "foo"), Group(user.id, "bar")) })

  // POST /groups?name=foo -> Group
  val postGroup: Router[RequestReader[Group]] =
    Post / "groups" /> (currentUser.map(_.id) :: param("name")).as[Group]

  // PUT /user/groups/:group -> User
  val putUserGroup: Router[RequestReader[User]] =
    Put / "user" / "groups" / string /> { group =>
      currentUser ~> { user => User(user.id, Seq(Group(0, group))) }
    }

  Await.ready(Httpx.serve(":8081", getUserGroups :+: postGroup :+: putUserGroup))
}
