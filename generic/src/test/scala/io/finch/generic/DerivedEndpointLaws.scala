package io.finch

import cats.Eq
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait DerivedEndpointLaws[A] extends Laws with MissingInstances with AllInstances {

  def endpoint: Endpoint[A]
  def toParams: A => Seq[(String, String)]

  def roundTrip(a: A): IsEq[A] = {
    val i = Input.get("/", toParams(a): _*)
    endpoint(i).awaitValueUnsafe().get <-> a
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
    )
}

object DerivedEndpointLaws {
  def apply[A](
    e: Endpoint[A],
    tp: A => Seq[(String, String)]
  ): DerivedEndpointLaws[A] = new DerivedEndpointLaws[A] {
    val endpoint: Endpoint[A] = e
    val toParams = tp
  }
}
