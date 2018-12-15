package io.finch.iteratee

import cats.effect.IO
import io.finch._
import io.iteratee.Enumerator
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class IterateeStreamingSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  checkAll("Iteratee.streamBody", StreamingLaws[Enumerator, IO](
    Enumerator.enumList,
    _.toVector.unsafeRunSync().toList
  ).all)

}
