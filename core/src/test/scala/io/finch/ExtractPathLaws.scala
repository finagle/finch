package io.finch

import cats.effect.Effect
import cats.instances.AllInstances
import io.netty.handler.codec.http.QueryStringEncoder
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws
import scala.reflect.ClassTag

abstract class ExtractPathLaws[F[_] : Effect, A]  extends Laws with MissingInstances with AllInstances {
  def decode: DecodePath[A]
  def one: Endpoint[F, A]
  def tail: Endpoint[F, Seq[A]]

  def all(implicit A: Arbitrary[Input]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "extractOne" -> Prop.forAll { input: Input =>
      val i = input.withRoute(input.route.map(s => new QueryStringEncoder(s).toString))
      val o = one(i)
      val v = i.route.headOption.flatMap(s => decode(s))

      o.awaitValueUnsafe() == v &&
        (v.isEmpty || o.remainder.contains(i.withRoute(i.route.tail)))
    },
    "extractTail" -> Prop.forAll { input: Input =>
      val i = input.withRoute(input.route.map(s => new QueryStringEncoder(s).toString))
      val o = tail(i)

      o.awaitValueUnsafe().contains(i.route.flatMap(decode.apply)) &&
        o.remainder.contains(i.copy(route = Nil))
    }
  )
}

object ExtractPathLaws {
  def apply[F[_] : Effect, A: DecodePath: ClassTag]: ExtractPathLaws[F, A] =
    new ExtractPathLaws[F, A] {
      def tail: Endpoint[F, Seq[A]] = Endpoint[F].paths[A]
      def one: Endpoint[F, A] = Endpoint[F].path[A]
      def decode: DecodePath[A] = DecodePath[A]
    }
}
