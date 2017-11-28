package io.finch

import java.util.UUID

import cats.Show
import cats.data.NonEmptyList
import io.finch.data.Foo

class ParamSpec extends FinchSpec {

  behavior of "param*"

  def withParam[A : Show](k: String)(v: A): Input = Input.get("/", k -> Show[A].show(v))

  checkAll("Param[String]", EntityEndpointLaws[String](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EntityEndpointLaws[Int](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EntityEndpointLaws[Long](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EntityEndpointLaws[Boolean](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EntityEndpointLaws[Float](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EntityEndpointLaws[Double](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EntityEndpointLaws[UUID](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Foo]", EntityEndpointLaws[Foo](paramOption("x"))(withParam("x")).evaluating)

  it should "throw an error if required param is missing" in {
    val endpoint: Endpoint[UUID] = param[UUID]("testEndpoint")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).awaitValueUnsafe()
    }
  }

  it should "throw an error if parameter is malformed" in {
    val endpoint: Endpoint[UUID] = param[UUID]("testEndpoint")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    }
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint: Endpoint[Seq[UUID]] = params[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint: Endpoint[NonEmptyList[UUID]] = paramsNel[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }
}
