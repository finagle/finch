package io.finch.benchmarks.service

import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.{Request, RequestBuilder, Response}
import com.twitter.io.Buf
import com.twitter.util.{Closable, Future, Await}
import java.net.{InetSocketAddress, URL}
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

/**
 * A benchmark for a simple user service, designed to help with comparing the
 * performance of Finch and the finagle-http API and the relative performance of
 * the different JSON libraries supported by Finch.
 *
 * The following command will run all user service benchmarks with reasonable
 * settings:
 *
 * > sbt "benchmarks/run -i 10 -wi 10 -f 2 -t 1 io.finch.benchmarks.service.*"
 */
@State(Scope.Thread)
abstract class UserServiceBenchmark(service: UserService) extends UserServiceApp(service) {
  @Setup
  def setUp(): Unit = setUpService()

  @TearDown
  def tearDown(): Unit = tearDownService()

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def userOperations(): Unit = runUserOperations()
}

class UserServiceApp(service: UserService) {
  var server: Closable = _
  var client: Service[Request, Response] = _

  def setUpService(): Unit = {
    server = Httpx.serve(new InetSocketAddress(8123), service.backend)
    client = Httpx.newService("localhost:8123")
  }

  def tearDownService(): Unit = {
    Await.ready(client.close())
    Await.ready(server.close())
  }

  protected val count = 10000

  protected val createUserRequests: Seq[Request] = (0 to count).map { i =>
    RequestBuilder().url(new URL("http://localhost:8123/users")).buildPost(
      Buf.Utf8(s"""{ "name": "name-${ "a" * i }", "age": $i }""")
    )
  }

  protected val updateUserRequests: Seq[Request] = (0 to count).map { i =>
    RequestBuilder().url(new URL("http://localhost:8123/users")).buildPut(
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
  }

  def callCreateUsers(start: Int, count: Int): Future[Seq[Response]] = Future.collect(
    createUserRequests.drop(start).take(count).map(client(_))
  )

  def callUpdateUsers(start: Int, count: Int): Future[Seq[Response]] = Future.collect(
    updateUserRequests.drop(start).take(count).map(client(_))
  )

  def callDeleteUsers: Future[Int] =
    client(RequestBuilder().url(new URL("http://localhost:8123/users")).buildDelete).map { resp =>
      resp.contentString.split(" ").head.toInt
    }

  def callGetAllUsers: Future[Response] =
    client(RequestBuilder().url(new URL("http://localhost:8123/users")).buildGet)

  def runUserOperations(): Unit = Await.result(
    for {
      batch1 <- callCreateUsers(   0, 500)
      batch2 <- callCreateUsers( 500, 500)
      batch3 <- callCreateUsers(1000, 500)
      batch4 <- callCreateUsers(1500, 500)
      batch5 <- callCreateUsers(2000, 500)
      _      <- callUpdateUsers(   0, 500)
      _      <- callUpdateUsers( 500, 500)
      _      <- callUpdateUsers(1000, 500)
      _      <- callUpdateUsers(1500, 500)
      _      <- callUpdateUsers(2000, 500)
      _      <- callGetAllUsers
      count  <- callDeleteUsers
    } yield assert(batch1.size + batch2.size + batch3.size + batch4.size + batch5.size == count)
  )

  def run(): Unit = {
    setUpService()
    runUserOperations()
    tearDownService()
  }
}
