package io.finch.benchmarks.service

import java.net.{InetSocketAddress, URL}

import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request, RequestBuilder, Response}
import com.twitter.io.Buf
import com.twitter.util.{Await, Closable, Future}

class UserServiceApp(service: () => UserService)(
  val port: Int,
  val count: Int,
  val batchSize: Int
) extends UserRequests {
  var server: Closable = _
  var client: Service[Request, Response] = _

  def setUpService(): Unit = {
    server = Http.serve(new InetSocketAddress(port), service().backend)
    client = Http.newService(s"127.0.0.1:$port")
    Await.result(batchCalls(Seq.tabulate(count)(createUserRequest)))
  }

  def tearDownService(): Unit = {
    Await.ready(client.close())
    Await.ready(server.close())
  }

  protected def batchCalls(calls: Seq[Request]): Future[Seq[Response]] =
    calls.grouped(batchSize).foldLeft(Future.value(Seq.empty[Response])) {
      case (acc, batch) =>
        for {
          oldResults <- acc
          newResults <- Future.collect(batch.map(client))
        } yield oldResults ++ newResults
    }

  def runCreateUsers: Future[Seq[Response]] = batchCalls(
    Seq.tabulate(count)(i => createUserRequest(i + count))
  )

  def runGetUsers: Future[Seq[Response]] = batchCalls(
    Seq.tabulate(count)(getUserRequest) ++ Seq.tabulate(count)(i => getUserRequest(i + count))
  )

  def runUpdateUsers: Future[Seq[Response]] = batchCalls(
    Seq.tabulate(count)(goodUpdateUserRequest) ++ Seq.tabulate(count)(badUpdateUserRequest)
  )

  def runGetAllUsers: Future[Response] = client(getAllUsersRequest)
  def runDeleteAllUsers: Future[Response] = client(deleteAllUsersRequest)
}

trait UserRequests {
  def port: Int

  private lazy val usersUrl = new URL(s"http://127.0.0.1:$port/users")

  protected def createUserRequest(i: Int): Request =
    RequestBuilder().url(usersUrl).buildPost(
      Buf.Utf8(s"""{ "name": "name-${ "a" * i }", "age": $i }""")
    )

  protected def getUserRequest(i: Int): Request =
    RequestBuilder().url(new URL(s"http://127.0.0.1:$port/users/$i")).buildGet

  protected def goodUpdateUserRequest(i: Int): Request =
    RequestBuilder().url(usersUrl).buildPut(
      Buf.Utf8(
        s"""
          {
            "id": $i,
            "name": "name-${ "b" * i }",
            "age": $i,
            "statuses": [
              { "message": "Foo" },
              { "message": "Bar" },
              { "message": "Baz" }
            ]
          }
        """
      )
    )

  protected def badUpdateUserRequest(i: Int): Request =
    RequestBuilder().url(usersUrl).buildPut(Buf.Utf8("foo"))

  protected def getAllUsersRequest: Request =
    RequestBuilder().url(usersUrl).buildGet

  protected def deleteAllUsersRequest: Request =
    RequestBuilder().url(usersUrl).buildDelete
}
