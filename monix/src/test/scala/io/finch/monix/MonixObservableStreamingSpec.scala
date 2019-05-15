package io.finch.monix

import cats.effect.{ConcurrentEffect, Effect, IO}
import com.twitter.io.Buf
import io.finch.{FinchSpec, StreamingLaws}
import monix.eval.TaskLift
import monix.execution.Scheduler
import monix.reactive.Observable
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class MonixObservableStreamingSpec extends FinchSpec with ScalaCheckDrivenPropertyChecks {

  implicit val s = Scheduler.global

  checkEffect[IO]
  checkConcurrentEffect[IO]

  def checkEffect[F[_]: TaskLift](implicit F: Effect[F]): Unit =
    checkAll("monixObservable.streamBody[F[_]: Effect]", StreamingLaws[ObservableF, F](
      list => Observable(list:_*),
      stream => F.toIO(stream.map(array => Buf.ByteArray.Owned(array)).toListL.to[F]).unsafeRunSync()
    ).all)

  def checkConcurrentEffect[F[_]: TaskLift](implicit F: ConcurrentEffect[F]): Unit =
    checkAll("monixObservable.streamBody[F[_]: ConcurrentEffect]", StreamingLaws[ObservableF, F](
      list => Observable(list:_*),
      stream => F.toIO(stream.map(array => Buf.ByteArray.Owned(array)).toListL.to[F]).unsafeRunSync()
    ).all)
}
