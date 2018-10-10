package io.finch

import java.util.UUID

import cats.{Eq, Show}
import cats.effect.IO
import io.finch.data.Foo
import org.scalacheck.Arbitrary

class HeaderSpec extends FinchSpec {

  behavior of "header*"

  def withHeader[A : Show](k: String)(v: A): Input = Input.get("/").withHeaders(k -> Show[A].show(v))

  checkAll("Header[String]",
    EntityEndpointLaws[IO, String](headerOption("x"))(withHeader("x"))
      .evaluating(Arbitrary(genNonEmptyString), Eq[String]))
  checkAll("Header[Int]", EntityEndpointLaws[IO, Int](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Long]", EntityEndpointLaws[IO, Long](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Boolean]", EntityEndpointLaws[IO, Boolean](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Float]", EntityEndpointLaws[IO, Float](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Double]", EntityEndpointLaws[IO, Double](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[UUID]", EntityEndpointLaws[IO, UUID](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Foo]", EntityEndpointLaws[IO, Foo](headerOption("x"))(withHeader("x")).evaluating)

  checkAll(
    "EvaluatingHeader[String]",
    EvaluatingEndpointLaws[IO, String](implicit de => header("foo")).all
  )

  it should "throw an error if required header is missing" in {
    val endpoint = header[UUID]("header")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).awaitValueUnsafe()
    }
  }

  it should "throw an error if header is malformed" in {
    val endpoint = header[UUID]("header")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index").withHeaders("header" -> "a")).awaitValueUnsafe()
    }
  }
}
