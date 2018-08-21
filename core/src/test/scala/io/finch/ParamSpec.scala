package io.finch

import java.util.UUID

import cats.Show
import cats.data.NonEmptyList
import com.twitter.util.Try
import io.finch.data.Foo
import io.finch.tried._

class ParamSpec extends FinchSpec {

  behavior of "param*"

  def withParam[A : Show](k: String)(v: A): Input = Input.get("/", k -> Show[A].show(v))

  checkAll("Param[String]", EntityEndpointLaws[Try, String](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EntityEndpointLaws[Try, Int](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EntityEndpointLaws[Try, Long](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EntityEndpointLaws[Try, Boolean](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EntityEndpointLaws[Try, Float](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EntityEndpointLaws[Try, Double](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EntityEndpointLaws[Try, UUID](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Foo]", EntityEndpointLaws[Try, Foo](paramOption("x"))(withParam("x")).evaluating)

  checkAll(
    "EvaluatingParam[String]",
    EvaluatingEndpointLaws[Try, String](implicit de => param("foo")).all
  )

  it should "throw an error if required param is missing" in {
    val endpoint: Endpoint[Try, UUID] = param[UUID]("testEndpoint")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).awaitValueUnsafe()
    }
  }

  it should "throw an error if parameter is malformed" in {
    val endpoint: Endpoint[Try, UUID] = param[UUID]("testEndpoint")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    }
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint: Endpoint[Try, Seq[UUID]] = params[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint: Endpoint[Try, NonEmptyList[UUID]] = paramsNel[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy (
      endpoint(Input.get("/index", "testEndpoint" -> "a")).awaitValueUnsafe()
    )
  }
}
