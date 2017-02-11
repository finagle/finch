package io.finch.data

import com.twitter.io.Buf
import com.twitter.util.Return
import io.finch.{Decode, DecodeEntity, Encode}
import io.finch.internal.HttpContent

case class Foo(s: String)

object Foo {
  implicit val decodeEntityFoo: DecodeEntity[Foo] =
    DecodeEntity.instance(s => Return(Foo(s)))

  implicit def decodeFoo[CT <: String]: Decode.Aux[Foo, CT] =
    Decode.instance((b, cs) => Return(Foo(b.asString(cs))))

  implicit def encodeFoo[CT <: String]: Encode.Aux[Foo, CT] =
    Encode.instance((f, cs) => Buf.ByteArray.Owned(f.s.getBytes(cs.name)))
}
