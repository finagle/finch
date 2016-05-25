package io.finch

import cats.Eq
import cats.laws._
import cats.laws.discipline._
import cats.std.AllInstances
import com.twitter.io.Buf
import org.scalacheck.{Prop, Arbitrary}
import org.typelevel.discipline.Laws
import shapeless.Witness

trait EncodeLaws[A, CT <: String] extends Laws with MissingInstances with AllInstances {

  def encode: Encode.Aux[A, CT]

  def roundTrip(a: A): IsEq[Buf] =
    encode(a) <-> Buf.Utf8(a.toString)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object EncodeLaws {
  def textPlain[A: Encode.TextPlain]: EncodeLaws[A, Witness.`"text/plain"`.T] =
    new EncodeLaws[A, Witness.`"text/plain"`.T] {
      val encode: Encode.Aux[A, Witness.`"text/plain"`.T] = implicitly[Encode.TextPlain[A]]
    }
}
