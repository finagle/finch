package io.finch

import cats.Eq
import cats.laws._
import cats.laws.discipline._
import cats.std.AllInstances
import com.twitter.io.Buf
import io.finch.internal.BufText
import java.nio.charset.Charset
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait EncodeLaws[A, CT <: String] extends Laws with MissingInstances with AllInstances {

  def encode: Encode.Aux[A, CT]

  def roundTrip(a: A, cs: Charset): IsEq[Buf] =
    encode(a, cs) <-> BufText(a.toString, cs)

  def all(implicit A: Arbitrary[A], CS: Arbitrary[Charset], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "all",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: A, cs: Charset) => roundTrip(a, cs) }
    )
}

object EncodeLaws {
  def text[A: Encode.Text]: EncodeLaws[A, Text.Plain] =
    new EncodeLaws[A, Text.Plain] {
      val encode: Encode.Aux[A, Text.Plain] = implicitly[Encode.Text[A]]
    }
}
