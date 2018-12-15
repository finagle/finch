package io.finch

import cats.effect.Effect
import com.twitter.finagle.http.Response
import com.twitter.io._
import io.finch.internal.futureToEffect
import io.finch.streaming.StreamFromReader
import io.iteratee.{Enumerator, Iteratee}
import shapeless.Witness

/**
  * Iteratee module
  */
package object iteratee extends IterateeInstances {


  implicit def enumeratorFromReader[F[_] : Effect]: StreamFromReader[Enumerator, F] =
    StreamFromReader.instance { reader =>
      def rec(reader: Reader[Buf]): Enumerator[F, Buf] = {
        Enumerator.liftM[F, Option[Buf]] {
          futureToEffect(reader.read())
        }.flatMap {
          case None => Enumerator.empty[F, Buf]
          case Some(buf) => Enumerator.enumOne[F, Buf](buf).append(rec(reader))
        }
      }
      rec(reader).ensure(Effect[F].delay(reader.discard()))
    }

  implicit def enumeratorToJsonResponse[F[_] : Effect, A](implicit
    e: Encode.Aux[A, Application.Json],
    w: Witness.Aux[Application.Json]
  ): ToResponse.Aux[Enumerator[F, A], Application.Json] = {
    mkToResponse[F, A, Application.Json](delimiter = Some(ToResponse.NewLine))
  }

}

trait IterateeInstances {

  implicit def enumeratorToResponse[F[_] : Effect, A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Enumerator[F, A], CT] = {
    mkToResponse[F, A, CT](delimiter = None)
  }

  protected def mkToResponse[F[_] : Effect, A, CT <: String](delimiter: Option[Buf])(implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Enumerator[F, A], CT] = {
    ToResponse.instance[Enumerator[F, A], CT]((enum, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      val iteratee = Iteratee.foreachM[F, Buf]((buf: Buf) => futureToEffect(writer.write(delimiter match {
        case Some(d) => buf.concat(d)
        case _ => buf
      })))
      val stream = enum
        .ensure(futureToEffect(writer.close()))
        .map(e.apply(_, cs))
        .into(iteratee)
      Effect[F].toIO(stream).unsafeRunAsyncAndForget()
      response
    })
  }
}
