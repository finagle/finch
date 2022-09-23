package io.finch

import cats.Show
import cats.effect.SyncIO

import java.util.UUID

class ParamSpec extends FinchSpec[SyncIO] {

  behavior of "param*"

  def withParam[A: Show](k: String)(v: A): Input = Input.get("/", k -> Show[A].show(v))

  checkAll("Param[String]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Foo]", EntityEndpointLaws(paramOption("x"))(withParam("x")).evaluating)

  checkAll(
    "EvaluatingParam[String]",
    EvaluatingEndpointLaws[SyncIO, String](implicit de => param("foo")).all
  )

  it should "throw an error if required param is missing" in {
    val endpoint = param[UUID]("testEndpoint")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).value.unsafeRunSync()
    }
  }

  it should "throw an error if parameter is malformed" in {
    val endpoint = param[UUID]("testEndpoint")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index", "testEndpoint" -> "a")).value.unsafeRunSync()
    }
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint = params[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy(
      endpoint(Input.get("/index", "testEndpoint" -> "a")).value.unsafeRunSync()
    )
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint = paramsNel[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy(
      endpoint(Input.get("/index", "testEndpoint" -> "a")).value.unsafeRunSync()
    )
  }
}
