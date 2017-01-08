package io.finch.generic

import cats.kernel.Eq
import io.finch._
import org.scalacheck.Arbitrary

class GenericSpec extends FinchSpec {

  behavior of "generic"

  case class Foo(a: String, b: Int)

  val e: Endpoint[Foo] = deriveEndpoint[Foo].fromParams

  implicit val eq: Eq[Foo] = Eq.fromUniversalEquals

  implicit val arbitraryFoo: Arbitrary[Foo] = Arbitrary(for {
    s <- Arbitrary.arbitrary[String]
    i <- Arbitrary.arbitrary[Int]
  } yield Foo(s, i))

  val f: Foo => Seq[(String, String)] = foo => Seq(
    ("a" -> foo.a),
    ("b" -> foo.b.toString)
  )

  checkAll("DerivedEndpoint[Foo]", DerivedEndpointLaws[Foo](e, f).evaluating)
}
