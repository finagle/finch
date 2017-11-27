package io.finch

import java.util.UUID

import cats.Show
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
}
