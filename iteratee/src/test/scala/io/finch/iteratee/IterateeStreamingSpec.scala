package io.finch.iteratee

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.IORuntime
import com.twitter.io.Buf
import io.finch._
import io.iteratee.Enumerator

class IterateeStreamingSpec extends FinchSpec[IO] with Dispatcher[IO] {
  def unsafeToFutureCancelable[A](fa: IO[A]) =
    fa.unsafeToFutureCancelable()(IORuntime.global)

  checkAll(
    "Iteratee.streamBody[IO]",
    StreamingLaws[Enumerator, IO](
      this,
      Enumerator.enumList,
      _.map(Buf.ByteArray.Owned.apply).toVector.map(_.toList)
    ).all
  )
}
