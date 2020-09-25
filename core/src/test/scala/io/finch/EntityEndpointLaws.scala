package io.finch

import scala.reflect.ClassTag

import cats.Eq
import cats.effect.Effect
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class EntityEndpointLaws[F[_]: Effect, A] extends Laws with MissingInstances with AllInstances {

  def decoder: DecodeEntity[A]
  def classTag: ClassTag[A]
  def endpoint: Endpoint[F, Option[A]]
  def serialize: A => Input

  def roundTrip(a: A): IsEq[Option[A]] = {
    val i = serialize(a)
    val e = endpoint
    e(i).awaitValueUnsafe().flatten <-> Some(a)
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll((a: A) => roundTrip(a))
    )
}

object EntityEndpointLaws {
  def apply[F[_]: Effect, A: DecodeEntity: ClassTag](
      e: Endpoint[F, Option[A]]
  )(f: A => Input): EntityEndpointLaws[F, A] = new EntityEndpointLaws[F, A] {
    val decoder: DecodeEntity[A] = DecodeEntity[A]
    val classTag: ClassTag[A] = implicitly[ClassTag[A]]
    val endpoint: Endpoint[F, Option[A]] = e
    val serialize: A => Input = f
  }
}
