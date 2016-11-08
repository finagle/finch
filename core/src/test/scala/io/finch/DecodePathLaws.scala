package io.finch

import cats.Eq
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait DecodePathLaws[A] extends Laws with MissingInstances with AllInstances {

  def capture: DecodePath[A]

  def roundTrip(a: A): IsEq[Option[A]] =
    capture(a.toString) <-> Some(a)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object DecodePathLaws {
  def apply[A: DecodePath]: DecodePathLaws[A] = new DecodePathLaws[A] {
    def capture: DecodePath[A] = DecodePath[A]
  }
}
