package io.finch.benchmarks.service

import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Closable, Future, Await}
import io.finch.{HttpRequest, HttpResponse}
import java.net.{InetSocketAddress, URL}

class UserServiceApp(service: () => UserService)(
  val port: Int,
  val count: Int,
  val batchSize: Int
) extends UserRequests {
  var server: Closable = _
  var client: Service[HttpRequest, HttpResponse] = _

  def setUpService(): Unit = {
    server = Httpx.serve(new InetSocketAddress(port), service().backend)
    client = Httpx.newService(s"127.0.0.1:$port")
    Await.result(batchCalls(Seq.tabulate(count)(createUserRequest)))
  }

  def tearDownService(): Unit = {
    Await.ready(client.close())
    Await.ready(server.close())
  }

  protected def batchCalls(calls: Seq[HttpRequest]): Future[Seq[HttpResponse]] =
    calls.grouped(batchSize).foldLeft(Future.value(Seq.empty[HttpResponse])) {
      case (acc, batch) =>
        for {
          oldResults <- acc
          newResults <- Future.collect(batch.map(client))
        } yield oldResults ++ newResults
    }

  def runCreateUsers: Future[Seq[HttpResponse]] = batchCalls(
    Seq.tabulate(count)(i => createUserRequest(i + count))
  )

  def runGetUsers: Future[Seq[HttpResponse]] = batchCalls(
    Seq.tabulate(count)(getUserRequest) ++ Seq.tabulate(count)(i => getUserRequest(i + count))
  )

  def runUpdateUsers: Future[Seq[HttpResponse]] = batchCalls(
    Seq.tabulate(count)(goodUpdateUserRequest) ++ Seq.tabulate(count)(badUpdateUserRequest)
  )

  def runGetAllUsers: Future[HttpResponse] = client(getAllUsersRequest)
  def runDeleteAllUsers: Future[HttpResponse] = client(deleteAllUsersRequest)
}

trait UserRequests {
  def port: Int

  private lazy val usersUrl = new URL(s"http://127.0.0.1:$port/users")

  protected def createUserRequest(i: Int): HttpRequest =
    RequestBuilder().url(usersUrl).buildPost(
      Buf.Utf8(s"""{ "name": "name-${ "a" * i }", "age": $i }""")
    )

  protected def getUserRequest(i: Int): HttpRequest =
    RequestBuilder().url(new URL(s"http://127.0.0.1:$port/users/$i")).buildGet

  protected def goodUpdateUserRequest(i: Int): HttpRequest =
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

  protected def badUpdateUserRequest(i: Int): HttpRequest =
    RequestBuilder().url(usersUrl).buildPut(Buf.Utf8("foo"))

  protected def getAllUsersRequest: HttpRequest =
    RequestBuilder().url(usersUrl).buildGet

  protected def deleteAllUsersRequest: HttpRequest =
    RequestBuilder().url(usersUrl).buildDelete
}
