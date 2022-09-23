package io.finch.iteratee

import cats.effect.IO
import com.twitter.io.Buf
import io.finch._
import io.iteratee.Enumerator

class IterateeStreamingSpec extends FinchSpec[IO] {
  checkAll(
    "Iteratee.streamBody[IO]",
    StreamingLaws[Enumerator, IO](
      Dispatchers.forIO,
      Enumerator.enumList,
      _.map(Buf.ByteArray.Owned.apply).toVector.map(_.toList)
    ).all
  )
}
