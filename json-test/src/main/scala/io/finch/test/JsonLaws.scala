package io.finch.test

import java.nio.charset.Charset

import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import cats.{Eq, Functor}
import com.twitter.io.Buf
import com.twitter.util._
import io.circe.jawn
import io.circe.{Decoder, Encoder}
import io.finch.internal.HttpContent
import io.finch.{DecodeStream, _}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait DecodeJsonLaws[A] extends Laws with AllInstances {
  def decode: Decode.Json[A]

  def success(a: A, cs: Charset)(implicit e: Encoder[A], d: Decoder[A]): IsEq[A] = {
    val json = e(a).noSpaces
    decode(Buf.ByteArray.Owned(json.getBytes(cs.name)), cs).right.get <-> jawn.decode(json).right.get
  }

  def failure(s: String, cs: Charset): Boolean =
    decode(Buf.ByteArray.Owned(s"NOT A JSON$s".getBytes(cs.name)), cs).isLeft

  def all(implicit
      a: Arbitrary[A],
      cs: Arbitrary[Charset],
      e: Encoder[A],
      d: Decoder[A],
      eq: Eq[A]
  ): RuleSet = new DefaultRuleSet(
    name = "decode",
    parent = None,
    "success" -> Prop.forAll((a: A, cs: Charset) => success(a, cs)),
    "failure" -> Prop.forAll((s: String, cs: Charset) => failure(s, cs))
  )
}

abstract class StreamJsonLaws[S[_[_], _], F[_], A](implicit
    F: Functor[S[F, ?]]
) extends Laws
    with AllInstances {

  def streamDecoder: DecodeStream.Json[S, F, A]

  def fromList: List[A] => S[F, A]

  def toList: S[F, A] => List[A]

  def success(a: List[A], cs: Charset)(implicit e: Encoder[A], eq: Eq[A]): IsEq[List[A]] = {
    val json = F.map(fromList(a))(a => e(a).noSpaces + "\n")
    val enum = F.map(json)(str => Buf.ByteArray.Owned(str.getBytes(cs.name)))
    toList(streamDecoder(enum, cs)) <-> a
  }

  def failure(a: A, cs: Charset)(implicit e: Encoder[A]): Boolean = {
    val json = F.map(fromList(a :: Nil))(a => e(a).noSpaces + "INVALID_JSON")
    val enum = F.map(json)(str => Buf.ByteArray.Owned(str.getBytes(cs.name)))
    Try(
      toList(streamDecoder(enum, cs))
    ).isThrow
  }

  def all(implicit
      a: Arbitrary[A],
      cs: Arbitrary[Charset],
      encode: Encoder[A],
      eq: Eq[A]
  ): RuleSet = new DefaultRuleSet(
    name = "enumerate",
    parent = None,
    "success" -> Prop.forAll((a: List[A], cs: Charset) => success(a, cs)),
    "failure" -> Prop.forAll((a: A, cs: Charset) => failure(a, cs))
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

  def streaming[S[_[_], _], F[_], A](
      streamFromList: List[A] => S[F, A],
      streamToList: S[F, A] => List[A]
  )(implicit
      decoder: DecodeStream.Json[S, F, A],
      functor: Functor[S[F, ?]]
  ): StreamJsonLaws[S, F, A] =
    new StreamJsonLaws[S, F, A] {
      val toList: S[F, A] => List[A] = streamToList
      val fromList: List[A] => S[F, A] = streamFromList
      val streamDecoder: DecodeStream.Json[S, F, A] = decoder
    }
}
