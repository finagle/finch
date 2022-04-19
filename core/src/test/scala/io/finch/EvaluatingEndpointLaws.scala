package io.finch

import cats.instances.AllInstances
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class EvaluatingEndpointLaws[F[_], A] extends Laws with MissingInstances with AllInstances {

  def decode: DecodeEntity[A]
  def endpoint(d: DecodeEntity[A]): Endpoint[F, A]

  def doNotEvaluateOnMatch(i: Input): Boolean = {
    val ed = new EvaluatingEndpointLaws.EvalDecodeEntity[A](decode)
    endpoint(ed)(i)
    !ed.evaluated
  }

  def all(implicit a: Arbitrary[Input]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "doNotEvaluateOnMatch" -> Prop.forAll((i: Input) => doNotEvaluateOnMatch(i))
  )
}

object EvaluatingEndpointLaws {

  private class EvalDecodeEntity[A](d: DecodeEntity[A]) extends DecodeEntity[A] {
    @volatile private var e = false
    def apply(s: String): Either[Throwable, A] = {
      e = true
      d(s)
    }

    def evaluated: Boolean = e
  }

  def apply[F[_], A: DecodeEntity](e: DecodeEntity[A] => Endpoint[F, A]): EvaluatingEndpointLaws[F, A] =
    new EvaluatingEndpointLaws[F, A] {
      val decode: DecodeEntity[A] = DecodeEntity[A]
      def endpoint(d: DecodeEntity[A]): Endpoint[F, A] = e(d)
    }
}
