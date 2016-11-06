package io.finch

import java.util.UUID

class ParamSpec extends FinchSpec {

  behavior of "param*"

  def withParam(k: String)(v: String): Input = Input.get("/", k -> v)

  checkAll("Param[String]", EntityEndpointLaws[String](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EntityEndpointLaws[Int](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EntityEndpointLaws[Long](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EntityEndpointLaws[Boolean](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EntityEndpointLaws[Float](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EntityEndpointLaws[Double](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EntityEndpointLaws[UUID](paramOption("x"))(withParam("x")).evaluating)
}
