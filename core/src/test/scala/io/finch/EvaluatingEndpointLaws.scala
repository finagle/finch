package io.finch

import cats.instances.AllInstances
import com.twitter.util.Try
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait EvaluatingEndpointLaws[A] extends Laws with MissingInstances with AllInstances {

  def decode: DecodeEntity[A]
  def endpoint(d: DecodeEntity[A]): Endpoint[A]

  def doNotEvaluateOnMatch(i: Input): Boolean = {
    val ed = new EvaluatingEndpointLaws.EvalDecodeEntity[A](decode)
    endpoint(ed)(i)
    !ed.evaluated
  }

  def all(implicit a: Arbitrary[Input]): RuleSet = new DefaultRuleSet(
    name = "all",
    parent = None,
    "doNotEvaluateOnMatch" -> Prop.forAll { (i: Input) => doNotEvaluateOnMatch(i) }
  )
}

object EvaluatingEndpointLaws {

  private class EvalDecodeEntity[A](d: DecodeEntity[A]) extends DecodeEntity[A] {
    @volatile private var e = false
    def apply(s: String): Try[A] = {
      e = true
      d(s)
    }

    def evaluated: Boolean = e
  }

  def apply[A: DecodeEntity](e: DecodeEntity[A] => Endpoint[A]): EvaluatingEndpointLaws[A] =
    new EvaluatingEndpointLaws[A] {
      val decode: DecodeEntity[A] = DecodeEntity[A]
      def endpoint(d: DecodeEntity[A]): Endpoint[A] = e(d)
    }
}
