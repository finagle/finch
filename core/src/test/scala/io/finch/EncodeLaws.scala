package io.finch

import cats.Eq
import cats.laws._
import cats.laws.discipline._
import cats.std.AllInstances
import com.twitter.io.{Buf, Charsets}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait EncodeLaws[A, CT <: String] extends Laws with MissingInstances with AllInstances {

  def encode: Encode.Aux[A, CT]

  def roundTrip(a: A): IsEq[Buf] =
    encode(a, Charsets.Utf8) <-> Buf.Utf8(a.toString)

  def all(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
  )
}

object EncodeLaws {
  def textPlain[A: Encode.Text]: EncodeLaws[A, Text.Plain] =
    new EncodeLaws[A, Text.Plain] {
      val encode: Encode.Aux[A, Text.Plain] = implicitly[Encode.Text[A]]
    }
}
