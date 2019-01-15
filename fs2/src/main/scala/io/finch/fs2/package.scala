package io.finch

import _root_.fs2.Stream
import cats.effect.Effect
import com.twitter.io.{Buf, Pipe, Reader, Writer}
import com.twitter.util.Future
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
  ): EncodeStream.Json[Stream, F, A] =
    new EncodeFs2Stream[F, A, Application.Json] {
      protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
      override protected def writeChunk(chunk: Buf, w: Writer[Buf]): Future[Unit] =
        w.write(chunk.concat(ToResponse.NewLine))
    }

  implicit def encodeSseFs2Stream[F[_]: Effect, A](implicit
    A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[Stream, F, A, Text.EventStream] =
    new EncodeFs2Stream[F, A, Text.EventStream] {
      protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
      override protected def writeChunk(chunk: Buf, w: Writer[Buf]): Future[Unit] =
        w.write(chunk.concat(ToResponse.NewLine))
    }

  implicit def encodeTextFs2Stream[F[_]: Effect, A](implicit
    A: Encode.Text[A]
  ): EncodeStream.Text[Stream, F, A] =
    new EncodeFs2Stream[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }
}

trait StreamInstances {
  protected abstract class EncodeFs2Stream[F[_], A, CT <: String](implicit
    F: Effect[F],
    TE: ToEffect[Future, F]
  ) extends EncodeStream[Stream, F, A] {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf
    protected def writeChunk(chunk: Buf, w: Writer[Buf]): Future[Unit] = w.write(chunk)

    def apply(s: Stream[F, A], cs: Charset): Reader[Buf] = {
      val p = new Pipe[Buf]
      val run = s
        .map(chunk => encodeChunk(chunk, cs))
        .evalMap(chunk => TE(writeChunk(chunk, p)))
        .onFinalize(F.suspend(TE(p.close())))
        .compile
        .drain

      F.toIO(run).unsafeRunAsyncAndForget()
      p
    }
  }

  implicit def encodeBufFs2[F[_]: Effect, CT <: String]: EncodeStream.Aux[Stream, F, Buf, CT] =
    new EncodeFs2Stream[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }

}
