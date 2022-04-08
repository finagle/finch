package io.finch

import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.io.Buf
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

import java.nio.charset.Charset

trait EncodeLaws[A, CT <: String] extends Laws with MissingInstances with AllInstances {

  def encode: Encode.Aux[A, CT]

  def roundTrip(a: A, cs: Charset): IsEq[Buf] =
    encode(a, cs) <-> Buf.ByteArray.Owned(a.toString.getBytes(cs))

  def all(implicit A: Arbitrary[A], CS: Arbitrary[Charset]): RuleSet =
    new DefaultRuleSet(
      name = "all",
      parent = None,
      "roundTrip" -> Prop.forAll((a: A, cs: Charset) => roundTrip(a, cs))
    )
}

object EncodeLaws {
  def text[A: Encode.Text]: EncodeLaws[A, Text.Plain] =
    new EncodeLaws[A, Text.Plain] {
      val encode: Encode.Aux[A, Text.Plain] = implicitly[Encode.Text[A]]
    }
}
