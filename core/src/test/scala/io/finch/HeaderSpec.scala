package io.finch

import java.util.UUID

import cats.{Eq, Show}
import com.twitter.util.Try
import io.finch.data.Foo
import io.finch.tried._
import org.scalacheck.Arbitrary

class HeaderSpec extends FinchSpec {

  behavior of "header*"

  def withHeader[A : Show](k: String)(v: A): Input = Input.get("/").withHeaders(k -> Show[A].show(v))

  checkAll("Header[String]",
    EntityEndpointLaws[Try, String](headerOption("x"))(withHeader("x"))
      .evaluating(Arbitrary(genNonEmptyString), Eq[String]))
  checkAll("Header[Int]", EntityEndpointLaws[Try, Int](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Long]", EntityEndpointLaws[Try, Long](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Boolean]", EntityEndpointLaws[Try, Boolean](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Float]", EntityEndpointLaws[Try, Float](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Double]", EntityEndpointLaws[Try, Double](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[UUID]", EntityEndpointLaws[Try, UUID](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Foo]", EntityEndpointLaws[Try, Foo](headerOption("x"))(withHeader("x")).evaluating)

  checkAll(
    "EvaluatingHeader[String]",
    EvaluatingEndpointLaws[Try, String](implicit de => header("foo")).all
  )

  it should "throw an error if required header is missing" in {
    val endpoint: Endpoint[Try, UUID] = header[UUID]("header")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).awaitValueUnsafe()
    }
  }

  it should "throw an error if header is malformed" in {
    val endpoint: Endpoint[Try, UUID] = header[UUID]("header")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index").withHeaders("header" -> "a")).awaitValueUnsafe()
    }
  }
}
