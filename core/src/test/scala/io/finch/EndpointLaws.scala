package io.finch

import algebra.Eq
import cats.laws.IsEq
import cats.std.AllInstances
import cats.laws._
import cats.laws.discipline._
import org.scalacheck.{Prop, Arbitrary}
import org.typelevel.discipline.Laws

import scala.reflect.ClassTag

trait EndpointLaws[A] extends Laws with MissingInstances with AllInstances {

  def decoder: Decode[A]
  def classTag: ClassTag[A]
  def endpoint: Endpoint[Option[String]]
  def serialize: String => Input

  def roundTrip(a: A): IsEq[Option[A]] = {
    val s = a.toString
    val i = serialize(s)
    val e = endpoint.as(decoder, classTag)
    e(i).value.flatten <-> (if (s.isEmpty) None else Some(a))
  }

  def evaluating(implicit A: Arbitrary[A], eq: Eq[A]): RuleSet =
    new DefaultRuleSet(
      name = "evaluating",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: A) => roundTrip(a) }
    )
}

object EndpointLaws {
  def apply[A: Decode: ClassTag](
    e: Endpoint[Option[String]]
  )(f: String => Input): EndpointLaws[A] = new EndpointLaws[A] {
    val decoder: Decode[A] = Decode[A]
    val classTag: ClassTag[A] = implicitly[ClassTag[A]]
    val endpoint: Endpoint[Option[String]] = e
    val serialize: String => Input = f
  }
}
