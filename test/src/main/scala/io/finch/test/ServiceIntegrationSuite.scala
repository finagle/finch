package io.finch.test

import com.twitter.finagle.{Httpx, ListeningServer, Service}
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Await
import org.scalatest.Outcome
import org.scalatest.fixture.FlatSpec

/**
 * Extends [[ServiceSuite]] to support integration testing for services.
 */
trait ServiceIntegrationSuite extends ServiceSuite { self: FlatSpec =>

  /**
   * Override in implementing classes if a different port is desired for
   * integration tests.
   */
  def port: Int = 8080

  /**
   * Provide a fixture containing a client that calls our locally-served
   * service.
   */
  override def withFixture(test: OneArgTest): Outcome = {
    val service: Service[Request, Response] = createService()
    var server: ListeningServer = Httpx.serve(s":$port", service)
    var client: Service[Request, Response] = Httpx.newService(s"127.0.0.1:$port")

    try {
      self.withFixture(test.toNoArgTest(FixtureParam(client)))
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
