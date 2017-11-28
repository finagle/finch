package io.finch

import java.util.UUID

import cats.{Eq, Show}
import io.finch.data.Foo
import org.scalacheck.Arbitrary

class HeaderSpec extends FinchSpec {

  behavior of "header*"

  def withHeader[A : Show](k: String)(v: A): Input = Input.get("/").withHeaders(k -> Show[A].show(v))

  checkAll("Header[String]",
    EntityEndpointLaws[String](headerOption("x"))(withHeader("x"))
      .evaluating(Arbitrary(genNonEmptyString), Eq[String]))
  checkAll("Header[Int]", EntityEndpointLaws[Int](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Long]", EntityEndpointLaws[Long](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Boolean]", EntityEndpointLaws[Boolean](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Float]", EntityEndpointLaws[Float](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Double]", EntityEndpointLaws[Double](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[UUID]", EntityEndpointLaws[UUID](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Foo]", EntityEndpointLaws[Foo](headerOption("x"))(withHeader("x")).evaluating)

  it should "throw an error if required header is missing" in {
    val endpoint: Endpoint[UUID] = header[UUID]("header")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).awaitValueUnsafe()
    }
  }

  it should "throw an error if header is malformed" in {
    val endpoint: Endpoint[UUID] = header[UUID]("header")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index").withHeaders("header" -> "a")).awaitValueUnsafe()
    }
  }
}
