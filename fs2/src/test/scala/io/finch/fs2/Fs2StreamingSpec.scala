package io.finch.fs2

import _root_.fs2.Stream
import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import com.twitter.io.Buf
import io.finch.ToResponse.streamToResponse
import io.finch.{FinchSpec, StreamingLaws}

class Fs2StreamingSpec extends FinchSpec[IO] with Dispatcher[IO] {
  def unsafeToFutureCancelable[A](fa: IO[A]) =
    fa.unsafeToFutureCancelable()(IORuntime.global)

  checkAll(
    "fs2.streamBody[IO]",
    StreamingLaws[Stream, IO](
      this,
      list => Stream(list: _*),
      _.map(Buf.ByteArray.Owned.apply).compile.toList
    ).all
  )
}
