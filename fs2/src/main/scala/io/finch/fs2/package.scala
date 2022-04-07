package io.finch

import java.nio.charset.Charset
import _root_.fs2.Stream
import cats.effect._
import cats.effect.kernel.Outcome.Canceled
import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.Future
import io.finch.internal._

package object fs2 extends StreamAsyncInstances {

  implicit def streamLiftReader[F[_]](implicit
      F: Sync[F],
      TA: ToAsync[Future, F]
  ): LiftReader[Stream, F] =
    new LiftReader[Stream, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): Stream[F, A] =
        Stream.repeatEval(F.defer(TA(reader.read()))).unNoneTerminate.map(process).onFinalize(F.delay(reader.discard()))
    }
}

trait StreamAsyncInstances extends StreamInstances  {

  implicit def encodeJsonAsyncFs2Stream[F[_]: Async, A](implicit
                                                        A: Encode.Json[A]
  ): EncodeStream.Json[F, Stream, A] =
    new EncodeNewLineDelimitedConcurrentFs2Stream[F, A, Application.Json]

  implicit def encodeSseAsyncFs2Stream[F[_]: Async, A](implicit
                                                       A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, Stream, A, Text.EventStream] =
    new EncodeNewLineDelimitedConcurrentFs2Stream[F, A, Text.EventStream]

  implicit def encodeTextAsyncFs2Stream[F[_]: Async, A](implicit
                                                        A: Encode.Text[A]
  ): EncodeStream.Text[F, Stream, A] =
    new EncodeConcurrentFs2Stream[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }

  implicit def encodeBufAsyncFs2[F[_]: Async, CT <: String]: EncodeStream.Aux[F, Stream, Buf, CT] =
    new EncodeConcurrentFs2Stream[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}

trait StreamInstances {

  final protected class EncodeNewLineDelimitedConcurrentFs2Stream[F[_]: Async, A, CT <: String](implicit
      A: Encode.Aux[A, CT]
  ) extends EncodeConcurrentFs2Stream[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  abstract protected class EncodeConcurrentFs2Stream[F[_], A, CT <: String](implicit
      F: Async[F],
      TA: ToAsync[Future, F]
  ) extends EncodeFs2Stream[F, A, CT] {

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]] =
      F.bracketCase(F.start(run))(_ => F.pure(reader)) {
        case (f, Canceled()) => f.cancel
        case _               => F.unit
      }
  }

  abstract protected class EncodeFs2Stream[F[_], A, CT <: String](implicit
      F: Sync[F],
      TA: ToAsync[Future, F]
  ) extends EncodeStream[F, Stream, A] {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    protected def dispatch(reader: Reader[Buf], run: F[Unit]): F[Reader[Buf]]

    def apply(s: Stream[F, A], cs: Charset): F[Reader[Buf]] = F.defer {
      val p = new Pipe[Buf]
      val run = s.map(chunk => encodeChunk(chunk, cs)).evalMap(chunk => TA(p.write(chunk))).onFinalize(F.defer(TA(p.close()))).compile.drain

      dispatch(p, run)
    }
  }
}
