package io.finch.iteratee

import cats.effect.IO
import com.twitter.io.Buf
import io.finch._
import io.iteratee.Enumerator
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class IterateeStreamingSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  checkAll("Iteratee.streamBody", StreamingLaws[Enumerator, IO, Buf, Text.Plain](
    Enumerator.enumList,
    _.toVector.unsafeRunSync().toList
  ).all)

}
