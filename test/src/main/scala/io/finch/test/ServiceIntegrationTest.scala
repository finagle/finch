package io.finch.petstore.test

import com.twitter.finagle.{Httpx, ListeningServer, Service}
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Await
import org.scalatest.fixture.FlatSpec

abstract class ServiceIntegrationTest(port: Int = 8080) extends ServiceTest {
  override def withFixture(test: OneArgTest) = {
    val service = createService()
    var server: ListeningServer = Httpx.serve(s":$port", service)
    var client: Service[Request, Response] = Httpx.newService(s"127.0.0.1:$port")

    try {
      withFixture(test.toNoArgTest(FixtureParam(client)))
    } finally {
      Await.ready(
        for {
          _ <- server.close()
          _ <- client.close()
          _ <- service.close()
        } yield ()
      )
    }
  }
}
