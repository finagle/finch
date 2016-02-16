package io.finch

import java.util.UUID

import com.twitter.finagle.http.Request

class ParamSpec extends FinchSpec {

  behavior of "param*"

  def withParam(k: String)(v: String): Input = Input(Request(k -> v))

  checkAll("Param[String]", EndpointLaws[String](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EndpointLaws[Int](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EndpointLaws[Long](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EndpointLaws[Boolean](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EndpointLaws[Float](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EndpointLaws[Double](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EndpointLaws[UUID](paramOption("x"))(withParam("x")).evaluating)
}
