package io.finch

import java.nio.charset.Charset

import _root_.fs2.Stream
import cats.effect._
import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.Future
import io.finch.internal._

package object fs2 extends StreamConcurrentInstances {

  implicit def streamLiftReader[F[_]](implicit
      F: Sync[F],
      TA: ToAsync[Future, F]
  ): LiftReader[Stream, F] =
    new LiftReader[Stream, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): Stream[F, A] =
        Stream.repeatEval(F.suspend(TA(reader.read()))).unNoneTerminate.map(process).onFinalize(F.delay(reader.discard()))
    }

  implicit def encodeBufConcurrentFs2[F[_]: ConcurrentEffect, CT <: String]: EncodeStream.Aux[F, Stream, Buf, CT] =
    new EncodeConcurrentFs2Stream[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}

trait StreamConcurrentInstances extends StreamEffectInstances {

  implicit def encodeJsonConcurrentFs2Stream[F[_]: ConcurrentEffect, A](implicit
      A: Encode.Json[A]
  ): EncodeStream.Json[F, Stream, A] =
    new EncodeNewLineDelimitedConcurrentFs2Stream[F, A, Application.Json]

  implicit def encodeSseConcurrentFs2Stream[F[_]: ConcurrentEffect, A](implicit
      A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, Stream, A, Text.EventStream] =
    new EncodeNewLineDelimitedConcurrentFs2Stream[F, A, Text.EventStream]

  implicit def encodeTextConcurrentFs2Stream[F[_]: ConcurrentEffect, A](implicit
      A: Encode.Text[A]
  ): EncodeStream.Text[F, Stream, A] =
    new EncodeConcurrentFs2Stream[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }

  implicit def encodeBufEffectFs2[F[_]: Effect, CT <: String]: EncodeStream.Aux[F, Stream, Buf, CT] =
    new EncodeEffectFs2Stream[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}

trait StreamEffectInstances extends StreamInstances {

  implicit def encodeJsonEffectFs2Stream[F[_]: Effect, A](implicit
      A: Encode.Json[A]
  ): EncodeStream.Json[F, Stream, A] =
    new EncodeNewLineDelimitedEffectFs2Stream[F, A, Application.Json]

  implicit def encodeSseEffectFs2Stream[F[_]: Effect, A](implicit
      A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, Stream, A, Text.EventStream] =
    new EncodeNewLineDelimitedEffectFs2Stream[F, A, Text.EventStream]

  implicit def encodeTextEffectFs2Stream[F[_]: Effect, A](implicit
      A: Encode.Text[A]
  ): EncodeStream.Text[F, Stream, A] =
    new EncodeEffectFs2Stream[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }
}

trait StreamInstances {

  final protected class EncodeNewLineDelimitedConcurrentFs2Stream[F[_]: ConcurrentEffect, A, CT <: String](implicit
      A: Encode.Aux[A, CT]
  ) extends EncodeConcurrentFs2Stream[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  final protected class EncodeNewLineDelimitedEffectFs2Stream[F[_]: Effect, A, CT <: String](implicit
      A: Encode.Aux[A, CT]
  ) extends EncodeEffectFs2Stream[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  abstract protected class EncodeConcurrentFs2Stream[F[_], A, CT <: String](implicit
      F: Concurrent[F],
      TA: ToAsync[Future, F]
  ) extends EncodeFs2Stream[F, A, CT] {

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]] =
      F.bracketCase(F.start(run))(_ => F.pure(reader)) {
        case (f, ExitCase.Canceled) => f.cancel
        case _                      => F.unit
      }
  }

  abstract protected class EncodeEffectFs2Stream[F[_], A, CT <: String](implicit
      F: Effect[F],
      TA: ToAsync[Future, F]
  ) extends EncodeFs2Stream[F, A, CT]
      with (Either[Throwable, Unit] => IO[Unit]) {

    def apply(cb: Either[Throwable, Unit]): IO[Unit] = IO.unit

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]] =
      F.productR(F.runAsync(run)(this).to[F])(F.pure(reader))
  }

  abstract protected class EncodeFs2Stream[F[_], A, CT <: String](implicit
      F: Sync[F],
      TA: ToAsync[Future, F]
  ) extends EncodeStream[F, Stream, A] {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]]

    def apply(s: Stream[F, A], cs: Charset): F[Reader[Buf]] = F.suspend {
      val p = new Pipe[Buf]
      val run = s.map(chunk => encodeChunk(chunk, cs)).evalMap(chunk => TA(p.write(chunk))).onFinalize(F.suspend(TA(p.close()))).compile.drain

      dispatch(p, run)
    }
  }
}
