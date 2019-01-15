package io.finch

import cats.effect.Effect
import com.twitter.io._
import com.twitter.util.Future
import io.finch.internal._
import io.finch.items.RequestItem
import io.iteratee.{Enumerator, Iteratee}
import java.nio.charset.Charset

package object iteratee extends IterateeInstances {

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  @deprecated("Use Endpoint.streamJsonBody or Endpoint.streamBinaryBody instead", "0.27.0")
  def enumeratorBody[F[_], A, CT <: String](implicit
    F: Effect[F],
    LR: LiftReader[Enumerator, F],
    decode: DecodeStream.Aux[Enumerator, F, A, CT]
  ): Endpoint[F, Enumerator[F, A]] = new Endpoint[F, Enumerator[F, A]] {
      final def apply(input: Input): Endpoint.Result[F, Enumerator[F, A]] = {
        if (!input.request.isChunked) EndpointResult.NotMatched[F]
        else {
          val req = input.request
          EndpointResult.Matched(
            input,
            Trace.empty,
            F.pure(Output.payload(decode(LR(req.reader), req.charsetOrUtf8)))
          )
        }
      }

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "enumeratorBody"
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming JSON body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  @deprecated("Use Endpoint.streamJsonBody or Endpoint.streamBinaryBody instead", "0.27.0")
  def enumeratorJsonBody[F[_] : Effect, A](implicit
    decode: DecodeStream.Aux[Enumerator, F, A, Application.Json]
  ): Endpoint[F, Enumerator[F, A]] = enumeratorBody[F, A, Application.Json].withToString("enumeratorJsonBody")
}

trait IterateeInstances extends LowPriorityIterateeInstances {

  implicit def enumeratorLiftReader[F[_]](implicit
    F: Effect[F],
    TE: ToEffect[Future, F]
  ): LiftReader[Enumerator, F] =
    new LiftReader[Enumerator, F] {
      final def apply[A](reader: Reader[Buf], process: Buf => A): Enumerator[F, A] = {
        def loop(): Enumerator[F, A] = {
          Enumerator
            .liftM[F, Option[Buf]](F.suspend(TE(reader.read())))
            .flatMap {
              case None => Enumerator.empty[F, A]
              case Some(buf) => Enumerator.enumOne[F, A](process(buf)).append(loop())
            }
        }

        loop().ensure(F.delay(reader.discard()))
      }
    }

  implicit def encodeJsonEnumerator[F[_]: Effect, A](implicit
    A: Encode.Json[A]
  ): EncodeStream.Json[Enumerator, F, A] =
    new EncodeNewLineDelimitedEnumerator[F, A, Application.Json]

  implicit def encodeSseEnumerator[F[_]: Effect, A](implicit
    A: Encode.Aux[A, Text.EventStream]
  ): EncodeStream.Aux[Enumerator, F, A, Text.EventStream] =
    new EncodeNewLineDelimitedEnumerator[F, A, Text.EventStream]

  implicit def encodeTextEnumerator[F[_]: Effect, A: Encode.Text](implicit
    A: Encode.Text[A]
  ): EncodeStream.Text[Enumerator, F, A] =
    new EncodeEnumerator[F, A, Text.Plain] {
      override protected def encodeChunk(chunk: A, cs: Charset): Buf = A(chunk, cs)
    }
}

trait LowPriorityIterateeInstances {

  protected final class EncodeNewLineDelimitedEnumerator[F[_]: Effect, A, CT <: String](implicit
    A: Encode.Aux[A, CT]
  ) extends EncodeEnumerator[F, A, CT] {
    protected def encodeChunk(chunk: A, cs: Charset): Buf =
      A(chunk, cs).concat(newLine(cs))
  }

  protected abstract class EncodeEnumerator[F[_], A, CT <: String](implicit
    F: Effect[F],
    TE: ToEffect[Future, F]
  ) extends EncodeStream[Enumerator, F, A] {

    type ContentType = CT

    protected def encodeChunk(chunk: A, cs: Charset): Buf

    private def writeIteratee(w: Writer[Buf]): Iteratee[F, Buf, Unit] =
      Iteratee.foreachM[F, Buf](chunk => TE(w.write(chunk)))

    def apply(s: Enumerator[F, A], cs: Charset): Reader[Buf] = {
      val p = new Pipe[Buf]
      val run = s
        .ensure(F.suspend(TE(p.close())))
        .map(chunk => encodeChunk(chunk, cs))
        .into(writeIteratee(p))

      F.toIO(run).unsafeRunAsyncAndForget()
      p
    }
  }

  implicit def encodeBufEnumerator[F[_]: Effect, CT <: String]: EncodeStream.Aux[Enumerator, F, Buf, CT] =
    new EncodeEnumerator[F, Buf, CT] {
      protected def encodeChunk(chunk: Buf, cs: Charset): Buf = chunk
    }
}
