package io.finch.data

import com.twitter.io.Buf
import io.finch.internal.HttpContent
import io.finch.{Decode, DecodeEntity, Encode}

case class Foo(s: String)

object Foo {
  implicit val decodeEntityFoo: DecodeEntity[Foo] =
    DecodeEntity.instance(s => Right(Foo(s)))

  implicit val decodeFoo: Decode.Text[Foo] =
    Decode.text((b, cs) => Right(Foo(b.asString(cs))))

  implicit val encodeFoo: Encode.Text[Foo] =
    Encode.text((f, cs) => Buf.ByteArray.Owned(f.s.getBytes(cs.name)))

  implicit def encodeList(implicit e: Encode.Text[Foo]): Encode.Text[List[Foo]] =
    Encode.text((fs, cs) => fs.map(f => e(f, cs)).reduce(_ concat _))
}
