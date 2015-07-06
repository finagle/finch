package io.finch.petstore.test

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Await
import org.scalatest.fixture.FlatSpec

abstract class ServiceTest extends FlatSpec {
  def createService(): Service[Request, Response]

  case class FixtureParam(service: Service[Request, Response])

  def withFixture(test: OneArgTest) = {
    val service = createService()

    try {
      withFixture(test.toNoArgTest(FixtureParam(service)))
    } finally {
      Await.ready(service.close())
    }
  }
}
