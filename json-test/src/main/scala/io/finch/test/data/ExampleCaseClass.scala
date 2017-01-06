package io.finch.test.data

import cats.Eq
import io.circe.{Decoder, Encoder}
import org.scalacheck.{Arbitrary, Gen}

case class ExampleCaseClass(a: String, b: Int, c: Boolean)

object ExampleCaseClass {
  implicit val exampleCaseClassArbitrary: Arbitrary[ExampleCaseClass] = Arbitrary(
    for {
      a <- Gen.alphaStr
      b <- Arbitrary.arbitrary[Int]
      c <- Arbitrary.arbitrary[Boolean]
    } yield ExampleCaseClass(a, b, c)
  )

  implicit val eq: Eq[ExampleCaseClass] = Eq.fromUniversalEquals

  implicit val encoder: Encoder[ExampleCaseClass] =
    Encoder.forProduct3[String, Int, Boolean, ExampleCaseClass]("a", "b", "c")(e => (e.a, e.b, e.c))

  implicit val decoder: Decoder[ExampleCaseClass] =
    Decoder.forProduct3("a", "b", "c")(ExampleCaseClass.apply)
}
