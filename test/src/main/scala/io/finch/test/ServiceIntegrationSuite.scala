package io.finch.test

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.util.Await
import org.scalatest.{FixtureTestSuite, Outcome}

/**
  * Extends [[ServiceSuite]] to support integration testing for services.
  */
trait ServiceIntegrationSuite extends ServiceSuite { self: FixtureTestSuite =>

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
    val server: ListeningServer = Http.serve(s":$port", service)
    val client: Service[Request, Response] = Http.newService(s"127.0.0.1:$port")

    try self.withFixture(test.toNoArgTest(FixtureParam(client)))
    finally Await.ready(
      for {
        _ <- server.close()
        _ <- client.close()
        _ <- service.close()
      } yield ()
    )
  }
}
