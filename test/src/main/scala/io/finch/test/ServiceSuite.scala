package io.finch.test

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Await, Duration}
import org.scalatest.{FixtureTestSuite, Outcome}

/**
  * A convenience class that is designed to make it easier to test HTTP services
  * both directly and in integration tests that are served locally. Implementing
  * classes must extend [[org.scalatest.FixtureTestSuite]] through [[org.scalatest.flatspec.FixtureAnyFlatSpec]]
  * for example.
  */
trait ServiceSuite { self: FixtureTestSuite =>

  /**
    * Create an instance of the service to be tested.
    */
  def createService(): Service[Request, Response]

  /**
    * The fixture type. Tests in this suite should take a [[FixtureParam]]
    * argument.
    */
  case class FixtureParam(service: Service[Request, Response]) {

    /**
      * Apply the service and await the response.
      */
    def apply(req: Request, timeout: Duration = Duration.fromSeconds(10)): Response =
      Await.result(service(req), timeout)
  }

  /**
    * By default we call the service directly, without serving it.
    */
  def withFixture(test: OneArgTest): Outcome = {
    val service = createService()

    try self.withFixture(test.toNoArgTest(FixtureParam(service)))
    finally Await.ready(service.close())
  }
}
