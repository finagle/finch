package io.finch

import _root_.fs2.Stream
import cats.effect.{Effect, IO}
import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.Future
import io.finch.internal._
import java.nio.charset.Charset

package object fs2 extends StreamInstances {

  implicit def streamLiftReader[F[_]](implicit
    F: Effect[F],
    TE: ToEffect[Future, F]
  ): LiftReader[Stream, F] =
    new LiftReader[Stream, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): Stream[F, A] = {
        Stream
          .repeatEval(F.suspend(TE(reader.read())))
          .unNoneTerminate
          .map(process)
          .onFinalize(F.delay(reader.discard()))
      }
    }

  implicit def encodeJsonFs2Stream[F[_]: Effect, A](implicit
    A: Encode.Json[A]
  ): EncodeStream.Json[F, Stream, A] =
    new EncodeNewLineDelimitedFs2Stream[F, A, Application.Json]

  implicit def encodeSseFs2Stream[F[_]: Effect, A](implicit
    A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, Stream, A, Text.EventStream] =
    new EncodeNewLineDelimitedFs2Stream[F, A, Text.EventStream]

  implicit def encodeTextFs2Stream[F[_]: Effect, A](implicit
    A: Encode.Text[A]
  ): EncodeStream.Text[F, Stream, A] =
    new EncodeFs2Stream[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }
}

trait StreamInstances {

  protected final class EncodeNewLineDelimitedFs2Stream[F[_]: Effect, A, CT <: String](implicit
    A: Encode.Aux[A, CT]
  ) extends EncodeFs2Stream[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  protected abstract class EncodeFs2Stream[F[_], A, CT <: String](implicit
    F: Effect[F],
    TE: ToEffect[Future, F]
  ) extends EncodeStream[F, Stream, A] with (Either[Throwable, Unit] => IO[Unit]) {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    def apply(cb: Either[Throwable, Unit]): IO[Unit] = IO.unit

    def apply(s: Stream[F, A], cs: Charset): F[Reader[Buf]] = {
      val p = new Pipe[Buf]
      val run = s
        .map(chunk => encodeChunk(chunk, cs))
        .evalMap(chunk => TE(p.write(chunk)))
        .onFinalize(F.suspend(TE(p.close())))
        .compile
        .drain

      F.productR(F.runAsync(run)(this).to[F])(F.pure(p))
    }
  }

  implicit def encodeBufFs2[F[_]: Effect, CT <: String]: EncodeStream.Aux[F, Stream, Buf, CT] =
    new EncodeFs2Stream[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}
