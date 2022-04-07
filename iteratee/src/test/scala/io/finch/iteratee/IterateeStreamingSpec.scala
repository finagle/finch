package io.finch.iteratee

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.twitter.io.Buf
import io.finch._
import io.iteratee.Enumerator
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class IterateeStreamingSpec extends FinchSpec with ScalaCheckDrivenPropertyChecks {

  checkAll(
    "Iteratee.streamBody",
    StreamingLaws[Enumerator, IO](
      Enumerator.enumList,
      _.map(array => Buf.ByteArray.Owned(array)).toVector.unsafeRunSync().toList
    ).all
  )

}
