package io.finch.fs2

import _root_.fs2.Stream
import cats.effect.IO
import com.twitter.io.Buf
import io.finch._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class Fs2StreamingSpec extends FinchSpec with ScalaCheckDrivenPropertyChecks {

  checkAll("fs2.streamBody", StreamingLaws[Stream, IO](
    list => Stream(list:_*),
    _.map(array => Buf.ByteArray.Owned(array)).compile.toList.unsafeRunSync()
  ).all)

}
