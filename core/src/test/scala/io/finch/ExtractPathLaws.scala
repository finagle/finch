package io.finch

import cats.effect.SyncIO
import io.netty.handler.codec.http.QueryStringEncoder
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

import scala.reflect.ClassTag

abstract class ExtractPathLaws[A] extends Laws with TestInstances {
  def decode: DecodePath[A]
  def one: Endpoint[SyncIO, A]
  def tail: Endpoint[SyncIO, List[A]]

  def all(implicit A: Arbitrary[Input]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "extractOne" -> Prop.forAll { input: Input =>
      val i = input.withRoute(input.route.map(s => new QueryStringEncoder(s).toString))
      val o = one(i)
      val v = i.route.headOption.flatMap(s => decode(s))
      o.valueOption.unsafeRunSync() == v &&
      (v.isEmpty || o.remainder.contains(i.withRoute(i.route.tail)))
    },
    "extractTail" -> Prop.forAll { input: Input =>
      val i = input.withRoute(input.route.map(s => new QueryStringEncoder(s).toString))
      val o = tail(i)

      o.valueOption.unsafeRunSync().contains(i.route.flatMap(decode.apply)) &&
      o.remainder.contains(i.copy(route = Nil))
    }
  )
}

object ExtractPathLaws {
  def apply[A: DecodePath: ClassTag]: ExtractPathLaws[A] =
    new ExtractPathLaws[A] {
      def tail: Endpoint[SyncIO, List[A]] = Endpoint[SyncIO].paths[A]
      def one: Endpoint[SyncIO, A] = Endpoint[SyncIO].path[A]
      def decode: DecodePath[A] = DecodePath[A]
    }
}
