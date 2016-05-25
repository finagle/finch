package io.finch

import java.util.UUID

import cats.kernel.Eq
import com.twitter.finagle.http.Request
import org.scalacheck.Arbitrary

class HeaderSpec extends FinchSpec {

  behavior of "header*"

  def withHeader(k: String)(v: String): Input = Input {
    val req = Request()
    req.headerMap.put(k, v)

    req
  }

  checkAll("Header[String]",
    EndpointLaws[String](headerOption("x"))(withHeader("x"))
      .evaluating(Arbitrary(genNonEmptyString), Eq[String]))
  checkAll("Header[Int]", EndpointLaws[Int](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Long]", EndpointLaws[Long](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Boolean]", EndpointLaws[Boolean](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Float]", EndpointLaws[Float](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[Double]", EndpointLaws[Double](headerOption("x"))(withHeader("x")).evaluating)
  checkAll("Header[UUID]", EndpointLaws[UUID](headerOption("x"))(withHeader("x")).evaluating)
}
