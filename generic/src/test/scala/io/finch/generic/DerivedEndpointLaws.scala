package io.finch.generic

import cats.Eq
import cats.effect.SyncIO
import cats.laws._
import cats.laws.discipline._
import io.finch._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class DerivedEndpointLaws[A] extends Laws with TestInstances {

  def endpoint: Endpoint[SyncIO, A]
  def toParams: A => Seq[(String, String)]

  def roundTrip(a: A): IsEq[A] = {
    val i = Input.get("/", toParams(a): _*)
    endpoint(i).value.unsafeRunSync() <-> a
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll((a: A) => roundTrip(a))
    )
}

object DerivedEndpointLaws {
  def apply[A](e: Endpoint[SyncIO, A], tp: A => Seq[(String, String)]): DerivedEndpointLaws[A] =
    new DerivedEndpointLaws[A] {
      val endpoint = e
      val toParams = tp
    }
}
