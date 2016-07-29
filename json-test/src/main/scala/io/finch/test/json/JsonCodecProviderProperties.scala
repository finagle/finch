package io.finch.test.json

import argonaut.{CodecJson, DecodeJson, EncodeJson, Parse}
import argonaut.Argonaut.{casecodec3, casecodec5}
import com.twitter.io.Buf.Utf8
import com.twitter.io.Charsets
import com.twitter.util.Return
import io.finch.{Decode, Encode}
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop.BooleanOperators
import org.scalatest.prop.Checkers
import org.scalatest.Matchers

case class ExampleCaseClass(a: String, b: Int, c: Boolean)
case class ExampleNestedCaseClass(
  string: String,
  double: Double,
  long: Long,
  ints: List[Int],
  example: ExampleCaseClass
)

object ExampleCaseClass {
  implicit val exampleCaseClassArbitrary: Arbitrary[ExampleCaseClass] = Arbitrary(
    for {
      a <- Gen.alphaStr
      b <- arbitrary[Int]
      c <- arbitrary[Boolean]
    } yield ExampleCaseClass(a, b, c)
  )
}

object ExampleNestedCaseClass {
  implicit val exampleNestedCaseClassArbitrary: Arbitrary[ExampleNestedCaseClass] = Arbitrary(
    for {
      s <- Gen.alphaStr
      d <- arbitrary[Double]
      l <- arbitrary[Long]
      i <- arbitrary[List[Int]]
      e <- arbitrary[ExampleCaseClass]
    } yield ExampleNestedCaseClass(s, d, l, i, e)
  )
}

/**
 * Provides tests that check properties about a library that provides JSON
 * codecs for use in Finch.
 */
trait JsonCodecProviderProperties { self: Matchers with Checkers =>
  import ArgonautCodecs._

  /**
   * Confirm that this encoder can encode instances of our case class.
   */
  def encodeNestedCaseClass(implicit ee: Encode.Json[ExampleNestedCaseClass]): Unit =
    check { (e: ExampleNestedCaseClass) =>
      Parse.decodeOption(Utf8.unapply(ee(e, Charsets.Utf8)).get)(exampleNestedCaseClassCodecJson) ===
        Some(e)
    }

  /**
   * Confirm that this encoder can decode instances of our case class.
   */
  def decodeNestedCaseClass(implicit decoder: Decode[ExampleNestedCaseClass]): Unit =
    check { (e: ExampleNestedCaseClass) =>
      decoder(exampleNestedCaseClassCodecJson.encode(e).nospaces) === Return(e)
    }

  /**
   * Confirm that this encoder fails on invalid input (both ill-formed JSON and
   * invalid JSON).
   */
  def failToDecodeInvalidJson(implicit decoder: Decode[ExampleNestedCaseClass]): Unit = {
    check { (badJson: String) =>
      Parse.decodeOption(badJson)(exampleNestedCaseClassCodecJson).isEmpty ==>
        decoder(badJson).isThrow
    }
    check { (e: ExampleCaseClass) =>
      decoder(exampleCaseClassCodecJson.encode(e).nospaces).isThrow
    }
  }

  /**
   * Confirm that this encoder can encode top-level lists of instances of our case class.
   */
  def encodeCaseClassList(implicit encoder: Encode.Json[List[ExampleNestedCaseClass]]): Unit =
    check { (es: List[ExampleNestedCaseClass]) =>
      Parse
        .decodeOption(Utf8.unapply(encoder(es, Charsets.Utf8))
        .getOrElse(""))(exampleNestedCaseClassListCodecJson) === Some(es)
    }

  /**
   * Confirm that this encoder can decode top-level lists of instances of our case class.
   */
  def decodeCaseClassList(implicit decoder: Decode[List[ExampleNestedCaseClass]]): Unit =
    check { (es: List[ExampleNestedCaseClass]) =>
      decoder(exampleNestedCaseClassListCodecJson.encode(es).nospaces) === Return(es)
    }

  /**
   * Confirm that this encoder has the correct content type.
   */
  def checkContentType(implicit ee: Encode.Json[ExampleNestedCaseClass]): Unit = ()
}

/**
 * Provides trusted Argonaut codecs for evaluating the codecs provided by the
 * target of our tests. Note that we are intentionally not defining the
 * instances as implicits.
 */
object ArgonautCodecs {
  val exampleCaseClassCodecJson: CodecJson[ExampleCaseClass] =
    casecodec3(ExampleCaseClass.apply, ExampleCaseClass.unapply)("a", "b", "c")

  val exampleNestedCaseClassCodecJson: CodecJson[ExampleNestedCaseClass] =
    casecodec5(ExampleNestedCaseClass.apply, ExampleNestedCaseClass.unapply)(
      "string",
      "double",
      "long",
      "ints",
      "example"
    )(
      implicitly,
      implicitly,
      implicitly,
      implicitly,
      implicitly,
      implicitly,
      implicitly,
      implicitly,
      exampleCaseClassCodecJson,
      exampleCaseClassCodecJson
    )

  val exampleNestedCaseClassListCodecJson: CodecJson[List[ExampleNestedCaseClass]] =
    CodecJson.derived(
      EncodeJson.fromFoldable[List, ExampleNestedCaseClass](
        exampleNestedCaseClassCodecJson,
        scalaz.std.list.listInstance
      ),
      DecodeJson.CanBuildFromDecodeJson[ExampleNestedCaseClass, List](
        exampleNestedCaseClassCodecJson,
        implicitly
      )
    )
}
