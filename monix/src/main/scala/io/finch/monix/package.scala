package io.finch

import java.nio.charset.Charset

import _root_.monix.eval.TaskLift
import _root_.monix.reactive.Observable
import cats.effect._
import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.Future
import io.finch.internal.newLine
import io.finch.monix.ObservableF

package object monix extends ObservableConcurrentEffectInstances {

  type ObservableF[F[_], A] = Observable[A]

  implicit def aliasResponseToRealResponse[F[_], A, CT <: Application.Json](implicit
    tr: ToResponse.Aux[F, ObservableF[F, A], CT]
  ): ToResponse.Aux[F, Observable[A], CT] = tr

  implicit def observableLiftReader[F[_]](implicit
    F: Effect[F],
    TA: ToAsync[Future, F]
  ): LiftReader[ObservableF, F] =
    new LiftReader[ObservableF, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): ObservableF[F, A] = {
        Observable
          .repeatEvalF(F.suspend(TA(reader.read())))
          .takeWhile(_.isDefined)
          .collect { case Some(buf) => process(buf) }
          .guaranteeF(F.delay(reader.discard()))
      }
    }

  implicit def encodeBufConcurrentEffectObservable[F[_] : ConcurrentEffect : TaskLift, CT <: String]: EncodeStream.Aux[F, ObservableF, Buf, CT] =
    new EncodeConcurrentEffectObservable[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}

trait ObservableConcurrentEffectInstances extends ObservableEffectInstances {

  implicit def encodeJsonConcurrentObservable[F[_] : ConcurrentEffect : TaskLift, A](implicit
    A: Encode.Json[A]
  ): EncodeStream.Json[F, ObservableF, A] =
    new EncodeNewLineDelimitedConcurrentEffectObservable[F, A, Application.Json]

  implicit def encodeSseConcurrentEffectObservable[F[_] : ConcurrentEffect : TaskLift, A](implicit
    A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, ObservableF, A, Text.EventStream] =
    new EncodeNewLineDelimitedConcurrentEffectObservable[F, A, Text.EventStream]

  implicit def encodeTextConcurrentEffectObservable[F[_] : ConcurrentEffect : TaskLift, A](implicit
    A: Encode.Text[A]
  ): EncodeStream.Text[F, ObservableF, A] =
    new EncodeConcurrentEffectObservable[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf =
        A(chunk, cs)
    }

  implicit def encodeBufEffectObservable[F[_] : Effect : TaskLift, CT <: String]: EncodeStream.Aux[F, ObservableF, Buf, CT] =
    new EncodeEffectObservable[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}

trait ObservableEffectInstances extends ObservableInstances {

  implicit def encodeJsonEffectObservable[F[_] : Effect : TaskLift, A](implicit
    A: Encode.Json[A]
  ): EncodeStream.Json[F, ObservableF, A] =
    new EncodeNewLineDelimitedEffectObservable[F, A, Application.Json]

  implicit def encodeSseEffectObservable[F[_] : Effect : TaskLift, A](implicit
    A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, ObservableF, A, Text.EventStream] =
    new EncodeNewLineDelimitedEffectObservable[F, A, Text.EventStream]

  implicit def encodeTextEffectObservable[F[_] : Effect : TaskLift, A](implicit
    A: Encode.Text[A]
  ): EncodeStream.Text[F, ObservableF, A] =
    new EncodeEffectObservable[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf =
        A(chunk, cs)
    }
}

trait ObservableInstances {

  protected final class EncodeNewLineDelimitedConcurrentEffectObservable[F[_] : ConcurrentEffect : TaskLift, A, CT <: String](implicit
    A: Encode.Aux[A, CT]
  ) extends EncodeConcurrentEffectObservable[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  protected final class EncodeNewLineDelimitedEffectObservable[F[_] : Effect : TaskLift, A, CT <: String](implicit
    A: Encode.Aux[A, CT]
  ) extends EncodeEffectObservable[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  protected abstract class EncodeConcurrentEffectObservable[F[_] : TaskLift, A, CT <: String](implicit
    F: ConcurrentEffect[F],
    TA: ToAsync[Future, F]
  ) extends EncodeObservable[F, A, CT] {
    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]] =
      F.bracketCase(F.start(run))(_ => F.pure(reader)) {
        case (f, ExitCase.Canceled) => f.cancel
        case _ => F.unit
      }
  }

  protected abstract class EncodeEffectObservable[F[_]: TaskLift, A, CT <: String](implicit
    F: Effect[F],
    TA: ToAsync[Future, F]
  ) extends EncodeObservable[F, A, CT] with (Either[Throwable, Unit] => IO[Unit]) {

    def apply(cb: Either[Throwable, Unit]): IO[Unit] = IO.unit

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]] =
      F.productR(F.runAsync(run)(this).to[F])(F.pure(reader))
  }

  protected abstract class EncodeObservable[F[_]: TaskLift, A, CT <: String](implicit
    F: Effect[F],
    TA: ToAsync[Future, F]
  ) extends EncodeStream[F, ObservableF, A] {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]]

    override def apply(s: ObservableF[F, A], cs: Charset): F[Reader[Buf]] =
      F.suspend {
        val p = new Pipe[Buf]

        val run = s
          .map(chunk => encodeChunk(chunk, cs))
          .mapEvalF(chunk => TA(p.write(chunk)))
          .guaranteeF(F.suspend(TA(p.close())))
          .completedL
          .to[F]

        dispatch(p, run)

      }
  }

}

