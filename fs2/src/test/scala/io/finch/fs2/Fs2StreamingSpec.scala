package io.finch.fs2

import _root_.fs2.Stream
import cats.effect.std.Dispatcher
import cats.effect.{Async, IO}
import com.twitter.io.Buf
import io.finch.ToResponse.streamToResponse
import io.finch.{FinchSpec, StreamingLaws}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class Fs2StreamingSpec extends FinchSpec with ScalaCheckDrivenPropertyChecks {

  checkAsync[IO]

  def checkAsync[F[_]: Async](implicit dispatcher: Dispatcher[F]): Unit =
    checkAll(
      "fs2.streamBody[F[_]: Async]",
      StreamingLaws[Stream, F](
        list => Stream(list: _*),
        stream => dispatcher.unsafeRunSync(stream.map(array => Buf.ByteArray.Owned(array)).compile.toList)
      ).all
    )
}
