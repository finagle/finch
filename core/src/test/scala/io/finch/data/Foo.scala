package io.finch.data

import cats.Show
import com.twitter.util.Return
import io.finch.DecodeEntity

case class Foo(s: String)
object Foo {
  implicit val showFoo: Show[Foo] =
    Show.show(_.s)

  implicit val decodeFoo: DecodeEntity[Foo] =
    DecodeEntity.instance(s => Return(Foo(s)))
}

