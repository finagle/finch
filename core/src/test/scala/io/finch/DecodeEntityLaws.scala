package io.finch

import cats.Eq
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.util.{Return, Try}
import org.scalacheck.{Prop, Arbitrary}
import org.typelevel.discipline.Laws

trait DecodeEntityLaws[A] extends Laws with MissingInstances with AllInstances {

  def decode: DecodeEntity[A]

  def roundTrip(a: A): IsEq[Try[A]] =
    decode(a.toString) <-> Return(a)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object DecodeEntityLaws {
  def apply[A: DecodeEntity]: DecodeEntityLaws[A] = new DecodeEntityLaws[A] {
    def decode: DecodeEntity[A] = DecodeEntity[A]
  }
}
