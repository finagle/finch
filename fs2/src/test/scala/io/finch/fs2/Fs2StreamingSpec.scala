package io.finch.fs2

import _root_.fs2.Stream
import cats.effect.IO
import com.twitter.io.Buf
import io.finch.ToResponse.streamToResponse
import io.finch.{Dispatchers, FinchSpec, StreamingLaws}

class Fs2StreamingSpec extends FinchSpec[IO] {
  checkAll(
    "fs2.streamBody[IO]",
    StreamingLaws[Stream, IO](
      Dispatchers.forIO,
      list => Stream(list: _*),
      _.map(Buf.ByteArray.Owned.apply).compile.toList
    ).all
  )
}
