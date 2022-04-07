package io.finch.generic

import cats.Eq
import cats.effect.std.Dispatcher
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import io.finch._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class DerivedEndpointLaws[F[_]: Dispatcher, A] extends Laws with MissingInstances with AllInstances {

  def endpoint: Endpoint[F, A]
  def toParams: A => Seq[(String, String)]

  def roundTrip(a: A): IsEq[A] = {
    val i = Input.get("/", toParams(a): _*)
    endpoint(i).awaitValueUnsafe().get <-> a
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll((a: A) => roundTrip(a))
    )
}

object DerivedEndpointLaws {
  def apply[F[_]: Dispatcher, A](
      e: Endpoint[F, A],
      tp: A => Seq[(String, String)]
  ): DerivedEndpointLaws[F, A] = new DerivedEndpointLaws[F, A] {
    val endpoint: Endpoint[F, A] = e
    val toParams = tp
  }
}
