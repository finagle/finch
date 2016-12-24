package io.finch.data

import cats.Show
import com.twitter.util.Return
import io.finch.{Decode, DecodeEntity}
import io.finch.internal._
import org.scalacheck.{Arbitrary, Gen}

case class Foo(s: String)

object Foo {
  implicit val showFoo: Show[Foo] =
    Show.show(_.s)

  implicit val decodeEntityFoo: DecodeEntity[Foo] =
    DecodeEntity.instance(s => Return(Foo(s)))

  implicit val decodeTextFoo: Decode.Text[Foo] =
    Decode.text((b, cs) => Return(Foo(BufText.extract(b, cs))))

  implicit val arbitraryFoo: Arbitrary[Foo] =
    Arbitrary(Gen.alphaStr.map(Foo.apply))
}

