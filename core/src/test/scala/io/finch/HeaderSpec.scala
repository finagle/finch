package io.finch

import cats.effect.SyncIO
import cats.{Eq, Show}
import org.scalacheck.Arbitrary

import java.util.UUID

class HeaderSpec extends FinchSpec[SyncIO] {

  behavior of "header*"

  def withHeader[A: Show](k: String)(v: A): Input = Input.get("/").withHeaders(k -> Show[A].show(v))

  checkAll("Header[String]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating(Arbitrary(genNonEmptyString), Eq[String]))
  checkAll("Header[Int]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Long]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Boolean]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Float]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Double]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[UUID]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Foo]", EntityEndpointLaws(headerOption("x"))(withHeader("x")).evaluating)

  checkAll(
    "EvaluatingHeader[String]",
    EvaluatingEndpointLaws[SyncIO, String](implicit de => header("foo")).all
  )

  it should "throw an error if required header is missing" in {
    val endpoint = header[UUID]("header")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).value.unsafeRunSync()
    }
  }

  it should "throw an error if header is malformed" in {
    val endpoint = header[UUID]("header")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index").withHeaders("header" -> "a")).value.unsafeRunSync()
    }
  }
}
