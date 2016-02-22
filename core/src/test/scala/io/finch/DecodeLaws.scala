package io.finch

import algebra.Eq
import cats.std.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.util.{Return, Try}
import org.scalacheck.{Prop, Arbitrary}
import org.typelevel.discipline.Laws

trait DecodeLaws[A] extends Laws with MissingInstances with AllInstances {

  def decode: Decode[A]

  def roundTrip(a: A): IsEq[Try[A]] =
    decode(a.toString) <-> Return(a)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object DecodeLaws {
  def apply[A: Decode]: DecodeLaws[A] = new DecodeLaws[A] {
    val decode: Decode[A] = Decode[A]
  }
}
