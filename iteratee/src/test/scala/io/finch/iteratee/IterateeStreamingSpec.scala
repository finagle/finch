package io.finch.iteratee

import cats.effect.IO
import com.twitter.io.Buf
import io.finch._
import io.iteratee.Enumerator
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class IterateeStreamingSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  checkAll("Iteratee.streamBody", StreamingLaws[Enumerator, IO](
    Enumerator.enumList,
    _.map(array => Buf.ByteArray.Owned(array)).toVector.unsafeRunSync().toList
  ).all)

}
