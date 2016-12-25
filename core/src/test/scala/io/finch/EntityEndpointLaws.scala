package io.finch

import cats.Eq
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws
import scala.reflect.ClassTag

trait EntityEndpointLaws[A] extends Laws with MissingInstances with AllInstances {

  def decoder: DecodeEntity[A]
  def classTag: ClassTag[A]
  def endpoint: Endpoint[Option[String]]
  def serialize: String => Input

  def roundTrip(a: A): IsEq[Option[A]] = {
    val s = a.toString
    val i = serialize(s)
    val e = endpoint.as(decoder, classTag)
    e(i).awaitValueUnsafe().flatten <-> Some(a)
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
    )
}

object EntityEndpointLaws {
  def apply[A: DecodeEntity: ClassTag](
    e: Endpoint[Option[String]]
  )(f: String => Input): EntityEndpointLaws[A] = new EntityEndpointLaws[A] {
    val decoder: DecodeEntity[A] = DecodeEntity[A]
    val classTag: ClassTag[A] = implicitly[ClassTag[A]]
    val endpoint: Endpoint[Option[String]] = e
    val serialize: String => Input = f
  }
}
