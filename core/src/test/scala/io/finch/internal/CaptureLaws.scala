package io.finch.internal

import algebra.Eq
import cats.std.AllInstances
import cats.laws._
import cats.laws.discipline._
import io.finch.MissingInstances
import org.scalacheck.{Prop, Arbitrary}
import org.typelevel.discipline.Laws

trait CaptureLaws[A] extends Laws with MissingInstances with AllInstances {

  def capture: Capture[A]

  def roundTrip(a: A): IsEq[Option[A]] =
    capture(a.toString) <-> Some(a)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object CaptureLaws {
  def apply[A: Capture] = new CaptureLaws[A] {
    def capture: Capture[A] = Capture[A]
  }
}
