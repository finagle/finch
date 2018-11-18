package io.finch

import _root_.fs2.Stream
import cats.effect.Effect
import com.twitter.finagle.http.Response
import com.twitter.io.Buf
import io.finch.internal.futureToEffect
import io.finch.streaming.StreamFromReader
import shapeless.Witness

package object fs2 extends StreamInstances {

  implicit def streamFromReader[F[_] : Effect]: StreamFromReader[Stream, F] = StreamFromReader.instance { reader =>
    Stream
      .repeatEval(futureToEffect(reader.read()))
      .unNoneTerminate
      .onFinalize(Effect[F].delay(reader.discard()))
  }

  implicit def streamToJsonResponse[F[_] : Effect, A](implicit
    e: Encode.Aux[A, Application.Json],
    w: Witness.Aux[Application.Json]
  ): ToResponse.Aux[Stream[F, A], Application.Json] = {
    mkToResponse[F, A, Application.Json](delimiter = Some(ToResponse.NewLine))
  }

}

trait StreamInstances {

  implicit def streamToResponse[F[_] : Effect, A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Stream[F, A], CT] = {
    mkToResponse[F, A, CT](delimiter = None)
  }

  protected def mkToResponse[F[_] : Effect, A, CT <: String](delimiter: Option[Buf])(implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Stream[F, A], CT] = {
    ToResponse.instance[Stream[F, A], CT]((stream, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      val effect = stream
        .map(e.apply(_, cs))
        .evalMap(buf => futureToEffect(writer.write(delimiter match {
          case Some(d) => buf.concat(d)
          case _ => buf
        })))
        .onFinalize(Effect[F].suspend(futureToEffect(writer.close())))
        .compile
        .drain

      Effect[F].toIO(effect).unsafeRunAsyncAndForget()
      response
    })
  }

}
