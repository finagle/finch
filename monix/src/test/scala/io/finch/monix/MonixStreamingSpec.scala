package io.finch.monix

import _root_.monix.tail.Iterant
import cats.effect.IO
import com.twitter.io.Buf
import io.finch._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class MonixStreamingSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  checkAll("Iterant.streamBody", StreamingLaws[Iterant, IO](
    list => Iterant.fromList(list),
    _.map(array => Buf.ByteArray.Owned(array)).toListL.unsafeRunSync()
  ).all)

}
