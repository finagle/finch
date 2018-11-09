package io.finch.test.data

import cats.Eq
import io.circe.{ Decoder, Encoder }
import org.scalacheck.{ Arbitrary, Gen }

case class ExampleNestedCaseClass(
  string: String,
  double: Double,
  long: Long,
  ints: List[Int],
  example: ExampleCaseClass
)

object ExampleNestedCaseClass {
  implicit val exampleNestedCaseClassArbitrary: Arbitrary[ExampleNestedCaseClass] = Arbitrary(
    for {
      s <- Gen.alphaStr
      d <- Arbitrary.arbitrary[Double]
      l <- Arbitrary.arbitrary[Long]
      i <- Arbitrary.arbitrary[List[Int]]
      e <- Arbitrary.arbitrary[ExampleCaseClass]
    } yield ExampleNestedCaseClass(s, d, l, i, e)
  )

  implicit val eq: Eq[ExampleNestedCaseClass] = Eq.fromUniversalEquals

  implicit val encoder: Encoder[ExampleNestedCaseClass] =
    Encoder.forProduct5[ExampleNestedCaseClass, String, Double, Long, List[Int], ExampleCaseClass](
      "string",
      "double",
      "long",
      "ints",
      "example"
    )(e => (e.string, e.double, e.long, e.ints, e.example))

  implicit val decoder: Decoder[ExampleNestedCaseClass] =
    Decoder.forProduct5("string", "double", "long", "ints", "example")(ExampleNestedCaseClass.apply)
}
