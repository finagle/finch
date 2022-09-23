package io.finch

import cats.ApplicativeThrow
import cats.effect.SyncIO
import cats.effect.std.Dispatcher
import io.netty.handler.codec.http.QueryStringEncoder
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

import scala.reflect.ClassTag

abstract class ExtractPathLaws[F[_], A] extends Laws with TestInstances {
  implicit def F: ApplicativeThrow[F]

  def dispatcher: Dispatcher[F]
  def decode: DecodePath[A]
  def one: Endpoint[F, A]
  def tail: Endpoint[F, List[A]]

  def all(implicit A: Arbitrary[Input]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "extractOne" -> Prop.forAll { input: Input =>
      val i = input.withRoute(input.route.map(s => new QueryStringEncoder(s).toString))
      val o = one(i)
      val v = i.route.headOption.flatMap(s => decode(s))
      dispatcher.unsafeRunSync(o.valueOption) == v &&
      (v.isEmpty || o.remainder.contains(i.withRoute(i.route.tail)))
    },
    "extractTail" -> Prop.forAll { input: Input =>
      val i = input.withRoute(input.route.map(s => new QueryStringEncoder(s).toString))
      val o = tail(i)
      dispatcher.unsafeRunSync(o.valueOption).contains(i.route.flatMap(decode.apply)) &&
      o.remainder.contains(i.copy(route = Nil))
    }
  )
}

object ExtractPathLaws {
  def apply[A: DecodePath: ClassTag]: ExtractPathLaws[SyncIO, A] =
    new ExtractPathLaws[SyncIO, A] {
      val F = ApplicativeThrow[SyncIO]
      val dispatcher = Dispatchers.forSyncIO
      val tail = Endpoint[SyncIO].paths[A]
      val one = Endpoint[SyncIO].path[A]
      val decode = DecodePath[A]
    }
}
