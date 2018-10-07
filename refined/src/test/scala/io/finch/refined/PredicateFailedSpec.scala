package io.finch.refined

import com.twitter.util.Throw
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import io.finch._
import io.finch.FinchSpec

class PredicateFailedSpec extends FinchSpec {

  it should "return error with predicate failure information" in {

    val endpoint = get(param[Int Refined Positive]("int")) { (i: Int Refined Positive) =>
      Ok(i.value)
    }

    val Some(Throw(result)) = endpoint(Input.get("/?int=-1")).awaitValue()

    result.getCause shouldBe a[PredicateFailed]
  }
}
