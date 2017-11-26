package io.finch

import java.util.UUID

class ParamSpec extends FinchSpec {

  behavior of "param*"

  def withParam[A](k: String)(v: A): Input = Input.get("/", k -> v.toString)

  checkAll("Param[String]", EntityEndpointLaws[String](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Int]", EntityEndpointLaws[Int](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Long]", EntityEndpointLaws[Long](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Boolean]", EntityEndpointLaws[Boolean](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Float]", EntityEndpointLaws[Float](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[Double]", EntityEndpointLaws[Double](paramOption("x"))(withParam("x")).evaluating)
  checkAll("Param[UUID]", EntityEndpointLaws[UUID](paramOption("x"))(withParam("x")).evaluating)
}
