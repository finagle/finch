package io.finch.playground

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.{Service, Filter, Httpx}
import com.twitter.util.{Future, Await}

import io.finch.{Endpoint => _, _}
import io.finch.micro._
import io.finch.request._
import io.finch.route.{Endpoint => _, _}
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

  case class AuthRequest(userId: Int, httpRequest: HttpRequest)
  val auth: Filter[HttpRequest, HttpResponse, AuthRequest, HttpResponse] =
    new Filter[HttpRequest, HttpResponse, AuthRequest, HttpResponse] {
      def apply(req: HttpRequest, service: Service[AuthRequest, HttpResponse]): Future[HttpResponse] =
        service(AuthRequest(10, req)) // always auth
    }

  // implicit view
  implicit val authReqIsHttpReq: AuthRequest %> HttpRequest = View(_.httpRequest)

  type AuthMicro[A] = PMicro[AuthRequest, A]
  type AuthEndpoint = PEndpoint[AuthRequest]

  val currentUser: AuthMicro[Int] = Micro(_.userId)

  // GET /user/groups -> Seq[Group]
  val getUserGroups: AuthMicro[Seq[Group]] =
    currentUser ~> { userId => Seq(Group(userId, "foo"), Group(userId, "bar")) }

  // POST /groups?name=foo -> Group
  val postGroup: AuthMicro[Group] =
    currentUser ~ param("name") ~> Group

  // PUT /user/groups/:group -> User
  def putUserGroup(group: String): AuthMicro[User] =
    currentUser ~> { User(_, Seq.empty[Group]) }

  // an API endpoint
  val api: AuthEndpoint =
    Get / "user" / "groups" /> getUserGroups |
    Post / "groups" /> postGroup |
    Put / "user" / "groups" / string /> putUserGroup

  Await.ready(Httpx.serve(":8081", auth andThen api))
}
