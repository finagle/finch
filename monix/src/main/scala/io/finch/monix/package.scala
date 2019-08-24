package io.finch

import _root_.monix.tail.Iterant
import cats.effect.{Effect, IO}
import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.Future
import io.finch.internal._
import java.nio.charset.Charset

package object monix extends IterantInstances {

  implicit def iterantLiftReader[F[_]](implicit
    F: Effect[F],
    TE: ToAsync[Future, F]
  ): LiftReader[Iterant, F] =
    new LiftReader[Iterant, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): Iterant[F, A] = {
        def loop(): Iterant[F, A] = {
          Iterant
            .liftF(F.suspend(TE(reader.read())))
            .flatMap {
              case None => Iterant.empty
              case Some(buf) => Iterant.eval(process(buf)) ++ loop()
            }
        }

        loop().guarantee(F.delay(reader.discard()))
      }
    }

  implicit def encodeJsonIterant[F[_]: Effect, A](implicit
    A: Encode.Json[A]
  ): EncodeStream.Json[F, Iterant, A] =
    new EncodeNewLineDelimitedIterant[F, A, Application.Json]

  implicit def encodeSseIterant[F[_]: Effect, A](implicit
    A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[F, Iterant, A, Text.EventStream] =
    new EncodeNewLineDelimitedIterant[F, A, Text.EventStream]

  implicit def encodeTextIterant[F[_]: Effect, A](implicit
    A: Encode.Text[A]
  ): EncodeStream.Text[F, Iterant, A] =
    new EncodeIterant[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }
}

trait IterantInstances {

  protected final class EncodeNewLineDelimitedIterant[F[_]: Effect, A, CT <: String](implicit
    A: Encode.Aux[A, CT]
  ) extends EncodeIterant[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  protected abstract class EncodeIterant[F[_], A, CT <: String](implicit
    F: Effect[F],
    TE: ToAsync[Future, F]
  ) extends EncodeStream[F, Iterant, A] with (Either[Throwable, Unit] => IO[Unit]) {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    def apply(cb: Either[Throwable, Unit]): IO[Unit] = IO.unit

    def apply(s: Iterant[F, A], cs: Charset): F[Reader[Buf]] = {
      val p = new Pipe[Buf]
      val run = s
        .map(chunk => encodeChunk(chunk, cs))
        .mapEval(chunk => TE(p.write(chunk)))
        .guarantee(F.suspend(TE(p.close())))
        .completedL

      F.productR(F.runAsync(run)(this).to[F])(F.pure(p))
    }
  }

  implicit def encodeBufIterant[F[_]: Effect, CT <: String]: EncodeStream.Aux[F, Iterant, Buf, CT] =
    new EncodeIterant[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}
