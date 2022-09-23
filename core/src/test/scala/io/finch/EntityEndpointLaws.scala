package io.finch

import cats.effect.std.Dispatcher
import cats.laws._
import cats.laws.discipline._
import cats.{ApplicativeThrow, Eq}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class EntityEndpointLaws[F[_], A] extends Laws with TestInstances {
  implicit def F: ApplicativeThrow[F]

  def decoder: DecodeEntity[A]
  def dispatcher: Dispatcher[F]
  def endpoint: Endpoint[F, Option[A]]
  def serialize: A => Input

  def roundTrip(a: A): IsEq[Option[A]] =
    dispatcher.unsafeRunSync(endpoint(serialize(a)).valueOption).flatten <-> Some(a)

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll((a: A) => roundTrip(a))
    )
}

object EntityEndpointLaws {
  def apply[F[_]: ApplicativeThrow, A: DecodeEntity](
      e: Endpoint[F, Option[A]],
      d: Dispatcher[F]
  )(f: A => Input): EntityEndpointLaws[F, A] =
    new EntityEndpointLaws[F, A] {
      val F = ApplicativeThrow[F]
      val decoder = DecodeEntity[A]
      val dispatcher = d
      val endpoint = e
      val serialize = f
    }
}
