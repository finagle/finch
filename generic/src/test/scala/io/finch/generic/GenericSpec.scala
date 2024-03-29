package io.finch.generic

import cats.effect.SyncIO
import cats.kernel.Eq
import io.finch.{Dispatchers, FinchSpec}
import org.scalacheck.Arbitrary

class GenericSpec extends FinchSpec[SyncIO] {

  behavior of "generic"

  case class Foo(a: String, b: Int)

  val e = deriveEndpoint[SyncIO, Foo].fromParams

  implicit val eq: Eq[Foo] =
    Eq.fromUniversalEquals

  implicit val arbitraryFoo: Arbitrary[Foo] = Arbitrary(for {
    s <- Arbitrary.arbitrary[String]
    i <- Arbitrary.arbitrary[Int]
  } yield Foo(s, i))

  val f: Foo => Seq[(String, String)] =
    foo => Seq("a" -> foo.a, "b" -> foo.b.toString)

  checkAll("DerivedEndpoint[Foo]", DerivedEndpointLaws(e, Dispatchers.forSyncIO)(f).evaluating)
}
