package io.finch.playgorund

import com.twitter.finagle.{Httpx, Service, Filter}
import com.twitter.util.{Await, Future}

import io.finch.{Endpoint => _, _}
import io.finch.request._
import io.finch.route._
import io.finch.response._

/**
 * GET /user/groups        -> Seq[Group]
 * POST /groups?name=foo   -> Group
 * PUT /user/groups/:group -> User
 */
object Main extends App {

  // model
  case class Group(name: String, ownerId: Int = 0)
  case class User(id: Int, groups: Seq[Group])

  // custom request
  case class AuthReq(http: HttpRequest, userId: Int)
  implicit val authReqEv: AuthReq => HttpRequest = _.http

  // GET /user/groups -> Seq[Group]
  def getUserGroups(userId: Int): Future[Seq[Group]] = Seq(Group("foo"), Group("bar")).toFuture

  // POST /groups?name=foo -> Group
  def postGroup(name: String, ownerId: Int): Future[Group] = Group(name, ownerId).toFuture

  // PUT /user/groups/:group -> User
  def putUserGroup(userId: Int, group: String): Future[User] = User(userId, Seq.empty[Group]).toFuture

  implicit val encodeGroup: EncodeResponse[Group] =
    EncodeResponse[Group]("application/json") { g =>
      s"""
        |{"name":"$${g.name}","owner":$${g.ownerId}}
      """.stripMargin
    }

  implicit def encodeUser(implicit e: EncodeResponse[Group]): EncodeResponse[User] =
    EncodeResponse[User]("application/json") { u =>
      s"""
        |{"id":$${u.id},"groups":[$${encodeSeq(e(u.groups))}]}
      """.stripMargin
    }

  implicit def encodeSeq[A](implicit encode: EncodeResponse[A]): EncodeResponse[Seq[A]] =
    EncodeResponse[Seq[A]]("application/json") { seq =>
      seq.map(encode(_)).mkString("[", ",", "]")
    }

  def service[Req, Rep](f: Req => Future[Rep]): Service[Req, Rep] = Service.mk(f)

  val endpoint: Endpoint[AuthReq, HttpResponse] = (
    Get / "user" / "groups" /> service[AuthReq, HttpResponse] { req =>
      getUserGroups(req.userId).map(Ok(_))
    }
  ) | (
    Post / "groups" /> service[AuthReq, HttpResponse] { req =>
      RequiredParam("group").embedFlatMap(postGroup(_, req.userId)).map(Ok(_))(req)
    }
  ) | (
    Put / "user" / "groups" / string /> { group =>
      service[AuthReq, HttpResponse] { req =>
        putUserGroup(req.userId, group).map(Ok(_))
      }
    }
  )

  val authorize = new Filter[HttpRequest, HttpResponse, AuthReq, HttpResponse] {
    def apply(req: HttpRequest, service: Service[AuthReq, HttpResponse]): Future[HttpResponse] = for {
      id <- OptionalHeader("X-User-Id")(req)
      rep <- service(AuthReq(req, id.getOrElse("0").toInt))
    } yield rep
  }

  val api: Service[HttpRequest, HttpResponse] =
    authorize andThen (endpoint: Service[AuthReq, HttpResponse])

  Await.ready(Httpx.serve(":8081", api))
}
