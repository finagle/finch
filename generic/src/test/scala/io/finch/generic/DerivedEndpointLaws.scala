package io.finch.generic

import cats.effect.std.Dispatcher
import cats.laws._
import cats.laws.discipline._
import cats.{ApplicativeThrow, Eq}
import io.finch._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class DerivedEndpointLaws[F[_], A] extends Laws with TestInstances {
  implicit def F: ApplicativeThrow[F]

  def dispatcher: Dispatcher[F]
  def endpoint: Endpoint[F, A]
  def toParams: A => Seq[(String, String)]

  def roundTrip(a: A): IsEq[A] = {
    val i = Input.get("/", toParams(a): _*)
    dispatcher.unsafeRunSync(endpoint(i).value) <-> a
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll((a: A) => roundTrip(a))
    )
}

object DerivedEndpointLaws {
  def apply[F[_]: ApplicativeThrow, A](
      e: Endpoint[F, A],
      d: Dispatcher[F]
  )(tp: A => Seq[(String, String)]): DerivedEndpointLaws[F, A] =
    new DerivedEndpointLaws[F, A] {
      val F = ApplicativeThrow[F]
      val dispatcher = d
      val endpoint = e
      val toParams = tp
    }
}
