package io.finch

import cats.instances.AllInstances
import io.netty.handler.codec.http.QueryStringEncoder
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws
import scala.reflect.ClassTag

trait ExtractPathLaws[A]  extends Laws with MissingInstances with AllInstances {
  def decode: DecodePath[A]
  def one: Endpoint[A]
  def tail: Endpoint[Seq[A]]

  def all(implicit A: Arbitrary[Input]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "extractOne" -> Prop.forAll { i: Input =>
      val encodedInput = i.withRoute(i.route.map(s => new QueryStringEncoder(s).toString))
      val o = one(i)
      val v = i.route.headOption.flatMap(s => decode(s))

      o.awaitValueUnsafe() == v &&
        (v.isEmpty || o.remainder.contains(i.withRoute(i.route.tail)))
    },
    "extractTail" -> Prop.forAll { i: Input =>
      val o = tail(i)

      o.awaitValueUnsafe().contains(i.route.flatMap(decode.apply)) &&
        o.remainder.contains(i.copy(route = Nil))
    }
  )
}

object ExtractPathLaws {
  def apply[A: DecodePath: ClassTag]: ExtractPathLaws[A] = new ExtractPathLaws[A] {
    def tail: Endpoint[Seq[A]] = paths[A]
    def one: Endpoint[A] = path[A]
    def decode: DecodePath[A] = DecodePath[A]
  }
}
