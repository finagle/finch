package io.finch

import java.util.UUID

import cats.Show
import cats.effect.IO
import io.finch.data.Foo

class ParamSpec extends FinchSpec {

  behavior of "param*"

  def withParam[A : Show](k: String)(v: A): Input = Input.get("/", k -> Show[A].show(v))

  checkAll("Param[String]", EntityEndpointLaws[IO, String](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EntityEndpointLaws[IO, Int](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EntityEndpointLaws[IO, Long](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EntityEndpointLaws[IO, Boolean](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EntityEndpointLaws[IO, Float](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EntityEndpointLaws[IO, Double](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EntityEndpointLaws[IO, UUID](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Foo]", EntityEndpointLaws[IO, Foo](paramOption("x"))(withParam("x")).evaluating)

  checkAll(
    "EvaluatingParam[String]",
    EvaluatingEndpointLaws[IO, String](implicit de => param("foo")).all
  )

  it should "throw an error if required param is missing" in {
    val endpoint = param[UUID]("testEndpoint")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).awaitValueUnsafe()
    }
  }

  it should "throw an error if parameter is malformed" in {
    val endpoint = param[UUID]("testEndpoint")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    }
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint = params[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint = paramsNel[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }
}
