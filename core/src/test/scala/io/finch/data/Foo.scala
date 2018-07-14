package io.finch.data

import cats.{Eq, Show}
import com.twitter.io.Buf
import com.twitter.util.Return
import io.finch.{Application, Decode, Encode}
import io.finch.internal.HttpContent
import org.scalacheck.{Arbitrary, Gen}

case class Foo(s: String)

object Foo {
  implicit val showFoo: Show[Foo] =
    Show.show(_.s)

  implicit val eqFoo: Eq[Foo] = Eq.fromUniversalEquals

  implicit val decodeTextFoo: Decode.Text[Foo] =
    Decode.text((b, cs) => Return(Foo(b.asString(cs))))

  implicit val decodeCsvFoo: Decode.Aux[Foo, Application.Csv] =
    Decode.instance((b, cs) =>
      Return(Foo(b.asString(cs).split("\n").last))
    )

  implicit val encodeCsvFoo: Encode.Aux[Foo, Application.Csv] =
    Encode.instance((a, cs) =>
      Buf.ByteArray.Owned(
      s"""|column
          |${a.s}""".stripMargin.getBytes(cs)
      )
    )

  implicit val arbitraryFoo: Arbitrary[Foo] =
    Arbitrary(Gen.alphaStr.suchThat(_.nonEmpty).map(Foo.apply))
}

