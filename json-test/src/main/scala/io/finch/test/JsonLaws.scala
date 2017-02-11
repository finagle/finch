package io.finch.test

import java.nio.charset.Charset

import cats.Eq
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.io.Buf
import io.circe.{Decoder, Encoder}
import io.circe.jawn
import io.finch._
import io.finch.internal.HttpContent
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait DecodeJsonLaws[A] extends Laws with AllInstances {
  def decode: Decode.Json[A]

  def success(a: A, cs: Charset)(implicit e: Encoder[A], d: Decoder[A]): IsEq[A] = {
    val json = e(a).noSpaces
    decode(Buf.ByteArray.Owned(json.getBytes(cs.name)), cs).get <-> jawn.decode(json).right.get
  }

  def failure(s: String, cs: Charset): Boolean =
    decode(Buf.ByteArray.Owned(s"NOT A JSON$s".getBytes(cs.name)), cs).isThrow

  def all(implicit
    a: Arbitrary[A],
    cs: Arbitrary[Charset],
    e: Encoder[A],
    d: Decoder[A],
    eq: Eq[A]
  ): RuleSet = new DefaultRuleSet(
    name = "decode",
    parent = None,
    "success" -> Prop.forAll { (a: A, cs: Charset) => success(a, cs) },
    "failure" -> Prop.forAll { (s: String, cs: Charset) => failure(s, cs) }
  )
}

trait EncodeJsonLaws[A] extends Laws with AllInstances {
  def encode: Encode.Json[A]

  def all(implicit a: Arbitrary[A], cs: Arbitrary[Charset], d: Decoder[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "encode",
      parent = None,
      "*" -> Prop.forAll { (a: A, cs: Charset) =>
        jawn.decode(encode(a, cs).asString(cs)).right.get <-> a
      }
    )
}

object JsonLaws {
  def encoding[A: Encode.Json]: EncodeJsonLaws[A] =
    new EncodeJsonLaws[A] {
      val encode: Encode.Json[A] = implicitly[Encode.Json[A]]
    }

  def decoding[A: Decode.Json]: DecodeJsonLaws[A] =
    new DecodeJsonLaws[A] {
      val decode: Decode.Json[A] = implicitly[Decode.Json[A]]
    }
}
