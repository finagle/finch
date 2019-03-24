package io.finch.fs2

import _root_.fs2.Stream
import cats.effect.{ConcurrentEffect, Effect, IO}
import com.twitter.io.Buf
import io.finch._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class Fs2StreamingSpec extends FinchSpec with ScalaCheckDrivenPropertyChecks {

  checkEffect[IO]
  checkConcurrentEffect[IO]

  def checkEffect[F[_]](implicit F: Effect[F]): Unit =
    checkAll("fs2.streamBody[F[_]: Effect]", StreamingLaws[Stream, F](
      list => Stream(list:_*),
      stream => F.toIO(stream.map(array => Buf.ByteArray.Owned(array)).compile.toList).unsafeRunSync()
    ).all)

  def checkConcurrentEffect[F[_]](implicit F: ConcurrentEffect[F]): Unit =
    checkAll("fs2.streamBody[F[_]: ConcurrentEffect]", StreamingLaws[Stream, F](
      list => Stream(list:_*),
      stream => F.toIO(stream.map(array => Buf.ByteArray.Owned(array)).compile.toList).unsafeRunSync()
    ).all)
}
