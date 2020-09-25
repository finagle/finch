package io.finch

import java.nio.charset.Charset

import cats.effect.{Async, Effect, IO}
import com.twitter.io._
import com.twitter.util.Future
import io.finch.internal._
import io.iteratee.{Enumerator, Iteratee}

package object iteratee extends IterateeInstances {

  implicit def enumeratorLiftReader[F[_]](implicit
      F: Async[F],
      TE: ToAsync[Future, F]
  ): LiftReader[Enumerator, F] =
    new LiftReader[Enumerator, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): Enumerator[F, A] = {
        def loop(): Enumerator[F, A] =
          Enumerator.liftM[F, Option[Buf]](F.suspend(TE(reader.read()))).flatMap {
            case None      => Enumerator.empty[F, A]
            case Some(buf) => Enumerator.enumOne[F, A](process(buf)).append(loop())
          }

        loop().ensure(F.delay(reader.discard()))
      }
    }

  implicit def encodeJsonEnumerator[F[_]: Effect, A](implicit
      A: Encode.Json[A],
      TE: ToAsync[Future, F]
  ): EncodeStream.Json[F, Enumerator, A] =
    new EncodeNewLineDelimitedEnumerator[F, A, Application.Json]

  implicit def encodeSseEnumerator[F[_]: Effect, A](implicit
      A: Encode.Aux[A, Text.EventStream],
      TE: ToAsync[Future, F]
  ): EncodeStream.Aux[F, Enumerator, A, Text.EventStream] =
    new EncodeNewLineDelimitedEnumerator[F, A, Text.EventStream]

  implicit def encodeTextEnumerator[F[_]: Effect, A](implicit
      A: Encode.Text[A],
      TE: ToAsync[Future, F]
  ): EncodeStream.Text[F, Enumerator, A] =
    new EncodeEnumerator[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }

}

trait IterateeInstances {

  final protected class EncodeNewLineDelimitedEnumerator[F[_]: Effect, A, CT <: String](implicit
      A: Encode.Aux[A, CT],
      TE: ToAsync[Future, F]
  ) extends EncodeEnumerator[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  abstract protected class EncodeEnumerator[F[_], A, CT <: String](implicit
      F: Effect[F],
      TE: ToAsync[Future, F]
  ) extends EncodeStream[F, Enumerator, A]
      with (Either[Throwable, Unit] => IO[Unit]) {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    private def writeIteratee(w: Writer[Buf]): Iteratee[F, Buf, Unit] =
      Iteratee.foreachM[F, Buf](chunk => TE(w.write(chunk)))

    final def apply(cb: Either[Throwable, Unit]): IO[Unit] = IO.unit

    def apply(s: Enumerator[F, A], cs: Charset): F[Reader[Buf]] = {
      val p = new Pipe[Buf]
      val run = s.ensure(F.suspend(TE(p.close()))).map(chunk => encodeChunk(chunk, cs)).into(writeIteratee(p))

      F.productR(F.runAsync(run)(this).to[F])(F.pure(p))
    }
  }

  implicit def encodeBufEnumerator[F[_]: Effect, CT <: String]: EncodeStream.Aux[F, Enumerator, Buf, CT] =
    new EncodeEnumerator[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}
