package io.finch

import algebra.Eq
import cats.std.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.util.{Return, Try}
import org.scalacheck.{Prop, Arbitrary}
import org.typelevel.discipline.Laws

trait DecoderLaws[A] extends Laws with MissingInstances with AllInstances {

  def decoder: DecodeRequest[A]

  def roundTrip(a: A): IsEq[Try[A]] =
    decoder(a.toString) <-> Return(a)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object DecoderLaws {
  def apply[A: DecodeRequest]: DecoderLaws[A] = new DecoderLaws[A] {
    val decoder: DecodeRequest[A] = DecodeRequest[A]
  }
}
