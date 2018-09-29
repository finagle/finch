package io.finch.generic

import cats.kernel.Eq
import com.twitter.util.Try
import io.finch.FinchSpec
import io.finch.tried._
import org.scalacheck.Arbitrary

class GenericSpec extends FinchSpec {

  behavior of "generic"

  case class Foo(a: String, b: Int)

  val e: Endpoint[Foo] = deriveEndpoint[Try, Foo].fromParams

  implicit val eq: Eq[Foo] = Eq.fromUniversalEquals

  implicit val arbitraryFoo: Arbitrary[Foo] = Arbitrary(for {
    s <- Arbitrary.arbitrary[String]
    i <- Arbitrary.arbitrary[Int]
  } yield Foo(s, i))

  val f: Foo => Seq[(String, String)] = foo => Seq(
    ("a" -> foo.a),
    ("b" -> foo.b.toString)
  )

  checkAll("DerivedEndpoint[Foo]", DerivedEndpointLaws[Try, Foo](e, f).evaluating)
}
