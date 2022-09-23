package io.finch.refined

import cats.effect.SyncIO
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import io.finch._

class PredicateFailedSpec extends FinchSpec[SyncIO] {
  it should "return error with predicate failure information" in {
    val endpoint = get(param[Int Refined Positive]("int")) { (i: Int Refined Positive) =>
      Ok(i.value)
    }

    inside(endpoint(Input.get("/?int=-1")).valueAttempt.unsafeRunSync()) { case Left(result) =>
      result.getCause shouldBe a[PredicateFailed]
    }
  }
}
